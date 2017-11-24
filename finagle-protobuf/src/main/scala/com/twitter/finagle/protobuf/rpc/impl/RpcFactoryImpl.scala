package com.twitter.finagle.protobuf.rpc.impl

import com.twitter.finagle.{SimpleFilter, Filter}
import com.twitter.finagle.protobuf.rpc.RpcFactory
import com.twitter.finagle.protobuf.rpc.RpcControllerWithOnFailureCallback
import com.twitter.finagle.protobuf.rpc.RpcServer
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.builder.ClientBuilder
import com.google.protobuf.RpcController
import com.google.protobuf.RpcChannel
import com.google.protobuf.Message
import com.google.protobuf.{Service => ProtobufService}
import java.util.concurrent.ExecutorService
import com.twitter.finagle.protobuf.rpc.ServiceExceptionHandler
import com.twitter.finagle.protobuf.rpc.ExceptionResponseHandler
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufRequest, ProtobufResponse}

import scala.language.reflectiveCalls

/**
  * A factory to create server and client stub instances.
  */
class RpcFactoryImpl extends RpcFactory {

  def createServer(sb: ServerBuilder[ProtobufRequest, ProtobufResponse, Any, Any, Any],
                   port: Int,
                   service: ProtobufService,
                   handler: ServiceExceptionHandler[Message],
                   executorService: ExecutorService,
                   filters: List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty ): RpcServer = new
      RpcServerImpl(sb, port, service, handler, executorService, filters)

  def createStub[T <: ProtobufService](cb: ClientBuilder[Any, Any, Any, Any, Any],
                                       service: {def newStub(c: RpcChannel): T},
                                       handler: ExceptionResponseHandler[Message],
                                       executorService: ExecutorService,
                                       version: Int = 1,
                                       filters: List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty): T = {
    service.newStub(new RpcChannelImpl(cb, service.asInstanceOf[T], handler, executorService, version, filters))
  }

  def createController(): RpcController = {
    new RpcControllerWithOnFailureCallback()
  }

  def release(stub: {def getChannel(): RpcChannel}) {
    stub.getChannel().asInstanceOf[RpcChannelImpl].release()
  }
}