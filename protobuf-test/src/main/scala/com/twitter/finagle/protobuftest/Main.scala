package com.twitter.finagle.protobuftest

import com.twitter.finagle.protobuftest.calculator._
import com.finagle.protobuf.test.calculator.FinagleTestRpc.FinagleCalculator
import com.twitter.util.CountDownLatch
import org.slf4j.{LoggerFactory, Logger}
import com.google.protobuf.Service
import java.net.InetSocketAddress

object Main {

  private val LOGGER: Logger = LoggerFactory.getLogger(Main.getClass)

  def main(args: Array[String]) {
    LOGGER.info("Protobuf Service Test: args: {}", args)

    if (args.length == 0) {
      createCalculatorServer
    }
    else {
      val serverType: String = args(0)
      serverType match {
        case "distributed" =>
          createDistributedCalculatorServer(args)
        case _ =>
          LOGGER.warn("unexpected server type({}), args: {}", Array(serverType, args).asInstanceOf[Array[AnyRef]])
      }
    }
  }

  def createDistributedCalculatorServer(args: Array[String]) {
    LOGGER.info("creating distributed calculator server: args: {}", args)
    if (args.length < 2) {
      throw new RuntimeException("for a distributed server, ip for dependent services must be specified")
    }
    val host: String = args(1)

    val server: ProtobufRpcServer[FinagleCalculator] = createDistributedServer(host)
    LOGGER.info("created distributed calculator server: port: {}", server.port)

    try {
      val latch: CountDownLatch = new CountDownLatch(1)
      latch.await()
    }
    finally {
      server.close()
    }
  }

  def createDistributedServer(host: String): ProtobufRpcServer[FinagleCalculator] = {
    LOGGER.info("createDistributedServer(): host: {}", host)

    val fClient = new FactorialClient(new InetSocketAddress(host, CalculatorFactory.calculatorPort))
    val mClient = new MultiplierClient(new InetSocketAddress(host, CalculatorFactory.calculatorPort))
    val sClient = new SummerClient(new InetSocketAddress(host, CalculatorFactory.calculatorPort))

    val server: DistributedCalculatorServerImpl = new DistributedCalculatorServerImpl(CalculatorFactory.calculatorPort, fClient, mClient, sClient)
    server.setZipkinTracer()
    server
  }

  def createCalculatorServer {
    val server: ProtobufRpcServer[FinagleCalculator] = CalculatorFactory.createCalculatorServer()
    server.setZipkinTracer()
    LOGGER.info("created calculator server: port: {}", server.port)

    try {
      val latch: CountDownLatch = new CountDownLatch(1)
      latch.await()
    }
    finally {
      server.close()
    }
  }
}
