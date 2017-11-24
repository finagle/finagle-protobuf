package com.twitter.finagle.protobuftest.calculator

import com.finagle.protobuf.test.calculator.FinagleTestRpc.{CalcRequest, CalcResponse}
import com.finagle.protobuf.test.calculator.dto.FinagleTestTypes.{InputListInteger, Result}

trait CalculatorProtobufUtil {

  def createCalcResponse(id: String, resultValue: Long): CalcResponse = {
    val builder: CalcResponse.Builder = CalcResponse.newBuilder()
    val resultBuilder: Result.Builder = Result.newBuilder()

    builder.setId(id)
    resultBuilder.setResult(resultValue)
    builder.setResult(resultBuilder)
    val response: CalcResponse = builder.build()
    response
  }

  def createCalcRequest(id: String, valueCount: Int): CalcRequest = {
    val builder: CalcRequest.Builder = CalcRequest.newBuilder()
    val inputBuilder: InputListInteger.Builder = InputListInteger.newBuilder()
    for (i <- 1 to valueCount) {
      inputBuilder.addValues(i)
    }

    builder.setId(id)
    builder.setTimestamp(System.currentTimeMillis())
    builder.setInput(inputBuilder)
    val request: CalcRequest = builder.build()
    request
  }

}
