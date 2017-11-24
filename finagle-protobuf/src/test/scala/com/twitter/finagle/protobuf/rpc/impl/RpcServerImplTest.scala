package com.twitter.finagle.protobuf.rpc.impl

import com.google.protobuf.{RpcCallback, RpcController}
import com.twitter.finagle.protobuf.test.Echo.{EchoRequest, EchoResponse, EchoService}
import org.scalatest.FunSuite

class RpcServerImplTest extends FunSuite {

  test("determine service name for direct implementing service") {
    val name = RpcServerImpl.determineServiceName(new WutEchoService)
    assert(name === "WutEchoService")
  }

  test("determine service name for anonymous inner class uses outer class") {
    val rpcService = new WutEchoService
    val name = RpcServerImpl.determineServiceName(rpcService.getClass.getName + "$1")
    assert(name === "WutEchoService")
  }

  test("determine service name for anonymous inner classes uses outer class") {
    val rpcService = new WutEchoService
    val name = RpcServerImpl.determineServiceName(rpcService.getClass.getName + "$1$35")
    assert(name === "WutEchoService")
  }

  class WutEchoService extends EchoService {
    def echo(controller: RpcController, request: EchoRequest, callback: RpcCallback[EchoResponse]): Unit = {
      /* nothing */
    }
  }

}
