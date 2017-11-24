package com.twitter.finagle.protobuf.rpc.impl

import com.google.protobuf.Descriptors.MethodDescriptor
import com.google.protobuf.RpcCallback
import com.google.protobuf.Message
import com.google.protobuf.RpcChannel
import com.google.protobuf.RpcController
import com.google.protobuf.{Service => ProtobufService}
import com.twitter.util.{Future, FuturePool}
import com.twitter.finagle.builder.ClientBuilder
import java.util.concurrent.ExecutorService
import com.twitter.finagle.protobuf.rpc.{RpcFactoryUtil, RpcControllerWithOnFailureCallback, ExceptionResponseHandler}
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest, ProtobufCodecFactory}
import com.twitter.finagle.{SimpleFilter, ChannelClosedException}
import org.slf4j.LoggerFactory

private[protobuf] object RpcChannelImpl {

  private val logger = LoggerFactory.getLogger(classOf[RpcChannelImpl])

  def determineServiceName(rpcService: ProtobufService): String = {
    // we can't just use SimpleName because we want to ignore inner classes e.g. $Stub
    val fullName = rpcService.getClass.getName
    val idx = fullName.lastIndexOf('.')
    val className = fullName.substring(idx + 1)

    // Service name will be an inner class of the protoc-generated wrapper
    val parts = className.split('$')
    val name = parts.length match {
      case 1 => className
      case _ => parts(1)
    }

    logger.debug(s"determineServiceName(): ${className} -> ${name}")
    name
  }

}

private[protobuf] class RpcChannelImpl(clientBuilder: ClientBuilder[Any, Any, Any, Any, Any],
                                       rpcService: ProtobufService,
                                       handler: ExceptionResponseHandler[Message],
                                       executorService: ExecutorService,
                                       version: Int = 1,
                                       filters:List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty) extends RpcChannel {

  private val logger = RpcChannelImpl.logger

  private val futurePool = FuturePool(executorService)
  private val serviceName = RpcChannelImpl.determineServiceName(rpcService)
  private val client: com.twitter.finagle.Service[ProtobufRequest, ProtobufResponse] =
    RpcFactoryUtil.applyFilters(clientBuilder.codec(
      new ProtobufCodecFactory(rpcService, serviceName, version)).name(serviceName + "-client").unsafeBuild(), filters)

  def callMethod(methodDescriptor: MethodDescriptor,
                 controller: RpcController,
                 requestMessage: Message,
                 responsePrototype: Message,
                 callback: RpcCallback[Message]): Unit = {
    val controllerImpl: RpcControllerWithOnFailureCallback = controller.asInstanceOf[RpcControllerWithOnFailureCallback]

    val methodName: String = methodDescriptor.getName
    logger.debug(s"sending request: ${serviceName}: method: (${methodName})")
    val request = ProtobufRequest(methodName, requestMessage, None)
    val future: Future[ProtobufResponse] = client(request)

    future onSuccess { response =>
      logger.debug(s"received response: ${serviceName}: method: (${methodName})")
      futurePool({
        handle(callback, controllerImpl, response.protobuf)
      })
    }

    future onFailure { throwable =>
      throwable match {
        case channelClosed: ChannelClosedException =>
          logger.debug(s"callMethod(${methodDescriptor.getFullName}) failed with ChannelClosedException - likely service request rejection or timeout", throwable)
          controllerImpl.setFailed(throwable)
        case e: RuntimeException =>
          logger.debug(s"callMethod(${methodDescriptor.getFullName}) failed", throwable)
          controllerImpl.setFailed(throwable)
        case t: Throwable =>
          logger.error(s"callMethod(${methodDescriptor.getFullName}) failed with throwable", throwable)
          controllerImpl.setFailed(throwable)
      }
    }
  }

  def release() {
    client.close()
  }

  private[this] def handle(callback: RpcCallback[Message], controller: RpcControllerWithOnFailureCallback, message: Message) {
    def doHandle(message: Message): RuntimeException = {
      try {
        handler.handle(message)
      }
      catch {
        case re: RuntimeException => new RuntimeException("encountered exception in exception response handler handle()", re)
        case t: Throwable => new RuntimeException("encountered throwable in exception response handler handle()", t)
      }
    }

    def handleResponseError: Boolean = {
      try {
        if (handler.canHandle(message)) {
          controller.setFailed(doHandle(message))
          true
        }
        else {
          false
        }
      }
      catch {
        case re: RuntimeException => controller
          .setFailed(new RuntimeException("encountered exception in exception response handler canHandle()", re)); true
        case t: Throwable => controller.setFailed(new RuntimeException("encountered throwable in exception response handler canHandle()", t)); true
      }
    }

    if (!handleResponseError) {
      callback.run(message)
    }
  }

}
