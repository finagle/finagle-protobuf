package com.twitter.finagle.protobuftest.calculator

import com.finagle.protobuf.test.calculator.dto.FinagleTestTypes.InputListInteger
import scala.collection.JavaConverters._
import com.twitter.finagle.protobuftest.util.JavaUtil

object CalculatorUtil {

  def sumInput(input: InputListInteger) = {
    doSum(JavaUtil.mapToScalaLong(input.getValuesList.asScala))
  }

  def multiplyInput(input: InputListInteger) = {
    doMultiply(JavaUtil.mapToScalaLong(input.getValuesList.asScala))
  }

  def factorialInput(input: InputListInteger) = {
    doFactorial(input.getValuesList.asScala(0).toInt)
  }

  def doSum(input: Seq[Long]) = {
    var result: Long = 0
    for (value <- input) {
      result += value
    }
    result
  }

  def doFactorial(input: Int) = {
    var list: List[Long] = List()
    for (v <- 1 to input) {
      v :: list
    }
    doMultiply(list)
  }

  def doMultiply(input: Seq[Long]) = {
    var result: Long = 1
    for (value <- input) {
      result *= value
    }
    result
  }

}
