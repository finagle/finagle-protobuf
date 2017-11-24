package com.twitter.finagle.protobuf.rpc

import com.google.protobuf.Message
import com.google.protobuf.RpcChannel
import com.google.protobuf.{Service => ProtobufService}
import com.twitter.finagle.SimpleFilter
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.builder.ServerBuilder
import com.google.protobuf.RpcController
import java.util.concurrent.ExecutorService

import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest}


// todo do we want to introduce more types like the first one does?
// todo should these take Filter instead of SimpleFilter?
/**
  * A factory to create server and client stub instances.
  */
trait RpcFactory {

  def createServer(sb: ServerBuilder[ProtobufRequest, ProtobufResponse, Any, Any, Any],
                   port: Int,
                   service: ProtobufService,
                   handler: ServiceExceptionHandler[Message],
                   executorService: ExecutorService,
                   filters: List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty): RpcServer

  def createStub[T <: ProtobufService](cb: ClientBuilder[Any, Any, Any, Any, Any],
                               service: {def newStub(c: RpcChannel): T},
                               handler: ExceptionResponseHandler[Message],
                               executorService: ExecutorService,
                               filters:List[SimpleFilter[ProtobufRequest,ProtobufResponse]]): T =
    createStub(cb, service, handler, executorService, 1, filters)

  def createStub[T <: ProtobufService](cb: ClientBuilder[Any, Any, Any, Any, Any],
                               service: {def newStub(c: RpcChannel): T},
                               handler: ExceptionResponseHandler[Message],
                               executorService: ExecutorService,
                               version: Int = 1,
                               filters: List[SimpleFilter[ProtobufRequest,ProtobufResponse]] = List.empty): T

  def createController(): RpcController

  def release(stub: {def getChannel(): RpcChannel})



}
