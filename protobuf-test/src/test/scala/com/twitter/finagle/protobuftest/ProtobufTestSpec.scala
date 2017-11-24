package com.twitter.finagle.protobuftest

import org.slf4j.{LoggerFactory, Logger}
import org.specs.SpecificationWithJUnit
import com.twitter.finagle.tracing._
import com.google.protobuf.Message
import java.util.concurrent.{CountDownLatch, CopyOnWriteArrayList, TimeUnit}
import java.net.InetSocketAddress
import com.google.common.collect.Lists
import scala.collection.JavaConversions._
import com.finagle.protobuf.test.calculator.FinagleTestRpc.{CalcResponse, CalcRequest, FinagleCalculator}
import com.twitter.finagle.protobuftest.calculator._
import com.twitter.finagle.protobuftest.util.FinagleUtil.RpcLatchCallback

object ProtobufTestSpec extends SpecificationWithJUnit with EndToEndMixin[FinagleCalculator] with CalculatorCallMixin {

  private val LOGGER: Logger = LoggerFactory.getLogger(ProtobufTestSpec.getClass)

  "A Zipkin Protobuf Test End to End Test" should {
        skip("requires running server")

    doBefore {
      setup()
    }
    doAfter {
      tearDown()
    }

    "execute one" in {
      LOGGER.warn("execute one")

      val testMethodName: String = "a-test-11"

      val stub: FinagleCalculator = client.get.stub
      val callCount = 1
      val callbacks: CopyOnWriteArrayList[CalcResponseCallbackHolder] = Lists.newCopyOnWriteArrayList()

      def callRpc() = {
        val latch = new CountDownLatch(callCount)
        for (i <- 0 to (callCount - 1)) {
          val id = i + 1
          val method: CalcServiceMethod = CalcServiceMethod.get(i)
          val result: (CalcRequest, RpcLatchCallback[CalcResponse]) = callStub(stub, latch, testMethodName + "-request-" + id, method)
          callbacks.add(new CalcResponseCallbackHolder(result._1, method, result._2))
        }

        if (latch.await(1000, TimeUnit.MILLISECONDS)) {
          LOGGER.info("after call: trace({}): {}", Trace.isActivelyTracing, Trace.idOption.getOrElse("no trace id"))
          callbacks.size() mustEqual callCount
          callbacks map {
            callback => callback.doAssert()
          }

          LOGGER.info("client tracer: {}", client.get.tracer)
        }
        else {
          fail("callback timed out")
        }
      }

      Trace.unwind {
        LOGGER.info("before push: trace({}): {}", Trace.isActivelyTracing, Trace.idOption.getOrElse("no trace id"))
        Trace.isActivelyTracing mustEq false
        Trace.pushTracerAndSetNextId(client.get.tracer)
        Trace.isActivelyTracing mustEq true
        Trace.recordRpcname(getClass.getSimpleName, testMethodName)

        Trace.record(Annotation.ClientSend())
        Trace.record("test start")
        LOGGER.info("trace({}): {}", Trace.isActivelyTracing, Trace.idOption.getOrElse("no trace id"))

        val startTime: Long = System.currentTimeMillis()
        try {
          callRpc()
        }
        finally {
          val endTime: Long = System.currentTimeMillis()
          LOGGER.info("test complete: start: {}, end: {}, elapsed: {}",
            Array(startTime, endTime, endTime - startTime + "ms").asInstanceOf[Array[AnyRef]])

          Trace.record("test end")
          Trace.record(Annotation.ClientRecv())
        }
      }
    }
  }

  override def setup(): Unit = {
    super.setup()

    client.get.setZipkinTracer()
  }

  override def host: String = "devsoa04"

  override def port: Int = CalculatorFactory.calculatorPort

  override def calculatorClient: RpcClient[FinagleCalculator] = client.get

  override def createClient(address: InetSocketAddress): RpcClient[FinagleCalculator] = {
    LOGGER.info("creating client: {}", address)
    CalculatorFactory.createClient(address)
  }

  class CalcResponseCallbackHolder(request: CalcRequest, method: CalcServiceMethod, callback: RpcLatchCallback[CalcResponse])
    extends ExpectedCallbackHolder[CalcRequest, CalcResponse](request, callback) {

    def doAssert() {
      callback.done mustEqual true
      val expected: Long = method match {
        case CalcServiceMethod.Sum() => CalculatorUtil.sumInput(request.getInput)
        case CalcServiceMethod.Multiply() => CalculatorUtil.multiplyInput(request.getInput)
        case CalcServiceMethod.Factorial() => CalculatorUtil.factorialInput(request.getInput)
      }
      callback.result.get.getId mustEqual request.getId
      callback.result.get.getResult.getResult mustEqual expected
    }
  }

  abstract class ExpectedCallbackHolder[I, M <: Message](input: I, callback: RpcLatchCallback[M]) {

    def doAssert()
  }

}
