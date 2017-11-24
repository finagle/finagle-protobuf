package com.twitter.finagle.protobuftest.calculator

import java.util.concurrent.CountDownLatch
import com.finagle.protobuf.test.calculator.FinagleTestRpc.{FinagleCalculator, CalcResponse, CalcRequest}
import com.google.protobuf.RpcController
import com.twitter.finagle.protobuftest.util.FinagleUtil.RpcLatchCallback
import com.twitter.finagle.protobuftest.util.FinagleUtil

import com.twitter.finagle.protobuftest.calculator.CalculatorFactory._

trait CalculatorCallMixin {

  def calculatorClient: RpcClient[FinagleCalculator]

  def callStub(stub: FinagleCalculator, latch: CountDownLatch, id: String,
    method: CalcServiceMethod): Tuple2[CalcRequest, RpcLatchCallback[CalcResponse]] = {
    val builder = createRequestBuilder(id)
    method match {
      case CalcServiceMethod.Factorial() =>
        addRandomInput(builder, 1, 10)
      case _ =>
        addRandomInput(builder)
    }
    val request: CalcRequest = builder.build()

    val controller: RpcController = calculatorClient.factory.createController()
    val callback: RpcLatchCallback[CalcResponse] = FinagleUtil.latchCallback(latch)

    method match {
      case CalcServiceMethod.Sum() => stub.sum(controller, request, callback)
      case CalcServiceMethod.Multiply() => stub.multiply(controller, request, callback)
      case CalcServiceMethod.Factorial() => stub.factorial(controller, request, callback)
    }

    (request, callback)
  }

}
