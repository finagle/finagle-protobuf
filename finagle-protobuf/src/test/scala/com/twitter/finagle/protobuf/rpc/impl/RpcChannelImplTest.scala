package com.twitter.finagle.protobuf.rpc.impl

import com.twitter.finagle.protobuf.test.Echo.EchoService
import org.scalatest.FunSuite

class RpcChannelImplTest extends FunSuite {
  test("determines service name") {
    val stub: EchoService.Stub = EchoService.newStub(null)
    val name = RpcChannelImpl.determineServiceName(stub)
    assert(name === "EchoService")
  }
}
