package com.twitter.finagle.protobuf.rpc.impl

import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}

import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest, ProtobufCodecFactory}
import com.twitter.finagle.protobuf.rpc.{RpcFactoryUtil, RpcServer, ServiceExceptionHandler}
import com.twitter.util._
import com.twitter.util.Duration
import com.twitter.util.FuturePool
import com.twitter.finagle.builder.{Server, ServerBuilder}
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import com.google.common.base.Preconditions
import com.google.protobuf.{Service => ProtobufService, DynamicMessage, Message, RpcCallback}
import com.google.protobuf.Descriptors._
import com.twitter.util.Promise
import org.slf4j.LoggerFactory
import com.twitter.finagle.builder.ServerConfig.Yes
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.{SimpleFilter, Status, Service}

private[protobuf] object RpcServerImpl {

  private val logger = LoggerFactory.getLogger(classOf[RpcServerImpl])

  def determineServiceName(rpcService: ProtobufService): String = {
    // we can't just use SimpleName because we want to ignore inner classes e.g. $1
    // In practice we are using the reflectiveService so we get an anonymous inner class
    determineServiceName(rpcService.getClass.getName)
  }

  def determineServiceName(rpcServiceClassName: String): String = {
    val idx = rpcServiceClassName.lastIndexOf('.')
    val className = rpcServiceClassName.substring(idx + 1)

    // Service name will be an inner class of the protoc-generated wrapper
    val parts: Array[String] = className.split('$')
    val name = parts.length match {
      case 0 => className
      case 1 => className
      case _ => parts(1)
    }

    logger.info(s"determineServiceName(): ${rpcServiceClassName} -> ${name}")
    name
  }
}

private[protobuf] class RpcServerImpl(sb: ServerBuilder[ProtobufRequest, ProtobufResponse, Any, Any, Any],
                                      port: Int,
                                      rpcService: ProtobufService,
                                      handler: ServiceExceptionHandler[Message],
                                      executorService: ExecutorService,
                                      filters: List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty,
                                      version: Int = 1) extends RpcServer {

  private val logger = LoggerFactory.getLogger(classOf[RpcServerImpl])

  Preconditions.checkNotNull(executorService)
  Preconditions.checkNotNull(handler)

  private val execFuturePool = new ExecutorServiceFuturePool(executorService)
  private val dispatcher = RpcFactoryUtil.applyFilters(ServiceDispatcher(rpcService, handler, execFuturePool),filters)

  private val server: Server = {
    val serviceName = RpcServerImpl.determineServiceName(rpcService)
    val codecFactory = new ProtobufCodecFactory(rpcService, serviceName, version)
    val builder: ServerBuilder[ProtobufRequest, ProtobufResponse, Yes, Yes, Yes] = sb.codec(codecFactory).name(serviceName)
      .bindTo(new InetSocketAddress(port))
    ServerBuilder.safeBuild(dispatcher, builder)
  }

  def close(duration: Duration) = {
    logger.debug("closing service  ...")

    Await.ready( dispatcher.close(duration) )
    logger.debug("... dispatcher closed")
    Await.ready( server.close(duration) )
    logger.debug( "service closed")
  }
}

private[protobuf] object ServiceDispatcher {

  private val logger = LoggerFactory.getLogger(classOf[ServiceDispatcher])

  def apply(service: ProtobufService, handler: ServiceExceptionHandler[Message], futurePool: FuturePool): ServiceDispatcher = {
    new ServiceDispatcher(service, handler, futurePool)
  }
}

private[protobuf] class ServiceDispatcher(service: ProtobufService, exceptionHandler: ServiceExceptionHandler[Message], futurePool: FuturePool)
  extends Service[ProtobufRequest, ProtobufResponse] {

  private val logger = ServiceDispatcher.logger
  private val isStopped = new AtomicBoolean(false)
  private val inFlightCount = new AtomicInteger(0)
  private var inFlightLatch : CountDownLatch = _

  // Available if not stopped
  override def status: Status = if (isStopped.get) Status.Closed else Status.Open

  override def close(after: Duration): Future[Unit] = {
    isStopped.set(true)

    inFlightLatch = new CountDownLatch( inFlightCount.get() )
    inFlightLatch.await(after)

    Future.Done
  }

  override def close(deadline: Time): Future[Unit] = {
    close( deadline.sinceNow )
  }

  def apply(request: ProtobufRequest) = {

    val methodName = request.methodName
    val requestMessage = request.protobuf
    logger.debug("received request: method: ({})", methodName)

    val method = service.getDescriptorForType.findMethodByName(methodName)
    if (method == null) {
      throw new java.lang.AssertionError("Should never happen, we already decoded method: " + methodName)
    }

    val promise = new Promise[ProtobufResponse]()
    val startTime: Long = System.currentTimeMillis()

    class RpcCallbackImpl extends RpcCallback[Message] {

      def run(response: Message) = {
        val endTime: Long = System.currentTimeMillis()
        val elapsed: Long = endTime - startTime
        Trace.record("pb-impl-end: elapsed: " + elapsed + "ms")
        logger.debug("impl complete: start: {}, end: {}, elapsed: {}", Array(startTime, endTime, elapsed + "ms").asInstanceOf[Array[AnyRef]])
        logger.debug("received response from impl: method: ({})", methodName)
        promise.setValue(ProtobufResponse(methodName, response, None))
      }
    }

    // dispatch to the service method
    val task = () => {
      if (isStopped.get()) {

        logger.warn("calling stopped dispatcher for method %s".format(methodName))
        Trace.recordBinary("ProtobufServiceImpl", "dispatcher stopped")
        val emptyResponseMessage = constructEmptyResponseMessage(method)
        val exception = new RuntimeException("Calling method in closed service")

        promise.setValue(ProtobufResponse(methodName, exceptionHandler.handle(exception, emptyResponseMessage) , None))
        promise
      }
      else {
        inFlightCount.incrementAndGet()

        try {
          Trace.record("pb-impl-start")
          service.callMethod(method, null, requestMessage, new RpcCallbackImpl)
        }
        catch {
          case exception: RuntimeException =>
            Trace.recordBinary("ProtobufServiceImpl", "exception")
            if (exceptionHandler.canHandle(exception)) {
              promise.setValue(ProtobufResponse(methodName, exceptionHandler.handle(exception, constructEmptyResponseMessage(method)), None))
            }
            else {
              // return an empty message if exception handler does not handle the exception
              logger.error("exception handler failed to handle exception, returning empty message", exception)
              promise.setValue(ProtobufResponse(methodName, constructEmptyResponseMessage(method), None))
            }
          case throwable: Throwable =>
            Trace.recordBinary("ProtobufServiceImpl", "error")
            logger.error("request error", throwable)
            promise.setValue(ProtobufResponse(methodName, constructEmptyResponseMessage(method), None))
        }
        finally {
          inFlightCount.decrementAndGet()
          if ( inFlightLatch != null ) {
            inFlightLatch.countDown()
          }
        }
      }
    }

    futurePool(task())
    promise
  }

  def constructEmptyResponseMessage(method: MethodDescriptor): Message = {
    val outputType = method.getOutputType
    DynamicMessage.newBuilder(outputType).build()
  }


}



