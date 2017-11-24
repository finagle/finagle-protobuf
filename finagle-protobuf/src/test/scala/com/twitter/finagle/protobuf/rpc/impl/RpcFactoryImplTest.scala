package com.twitter.finagle.protobuf.rpc.impl

import java.util.concurrent.Executors

import com.google.protobuf.RpcChannel
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.protobuf.test.Echo.EchoService
import com.twitter.finagle.protobuf.testUtil.TestExceptionResponseHandler
import com.twitter.util.Duration
import org.scalatest.FunSuite

class RpcFactoryImplTest extends FunSuite {

  val factory: RpcFactoryImpl = new RpcFactoryImpl()

  val executor = Executors.newSingleThreadExecutor

  val handler = new TestExceptionResponseHandler()

  val clientBuilder = ClientBuilder()
    .hosts("localhost:45459")
    .hostConnectionLimit(1)
    .retries(1)
    .requestTimeout(Duration.fromSeconds(1))
    .asInstanceOf[ClientBuilder[Any, Any, Any, Any, Any]]

  val newStub = EchoService.newStub(null).asInstanceOf[{def newStub(channel: RpcChannel): EchoService}]

  test("build version 0 successfully") {
    assertResult("EchoService") {
      val stub = factory.createStub(clientBuilder, newStub, handler, executor, 0, List.empty)
      stub.getDescriptorForType.getName
    }
  }

  test("build version 1 successfully") {
    assertResult("EchoService") {
      val stub = factory.createStub(clientBuilder, newStub, handler, executor, 1, List.empty)
      stub.getDescriptorForType.getName
    }
  }

  test("build negative version fails") {
    intercept[RuntimeException] {
      factory.createStub(clientBuilder, newStub, handler, executor, -1, List.empty)
    }
  }

  test("build version 2 fails") {
    intercept[RuntimeException] {
      factory.createStub(clientBuilder, newStub, handler, executor, 2, List.empty)
    }
  }
}
