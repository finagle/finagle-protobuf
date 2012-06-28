package com.twitter.finagle.protobuf.rpc

import java.util.concurrent.ExecutorService

trait RpcFactory {

  def createServer(sb: ServerBuilder[(String, Message), (String, Message), Any, Any, Any], port: Int, service: Service, handler: ServiceExceptionHandler[Message], executorService: ExecutorService): RpcServer

  def createStub[T <: Service](cb: ClientBuilder[(String, Message), (String, Message), Any, Any, Any], service: { def newStub(c: RpcChannel): T }, handler: ExceptionResponseHandler[Message], executorService: ExecutorService): T

  def createController(): RpcController

  def release(stub: { def getChannel(): RpcChannel })

}
