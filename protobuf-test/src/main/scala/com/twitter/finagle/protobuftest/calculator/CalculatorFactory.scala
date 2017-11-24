package com.twitter.finagle.protobuftest.calculator

import com.finagle.protobuf.test.calculator.FinagleTestRpc._
import org.slf4j.{LoggerFactory, Logger}
import java.util.concurrent.atomic.AtomicInteger
import com.google.protobuf.{Service => ProtobufService, Message, RpcChannel, RpcCallback, RpcController}
import com.finagle.protobuf.test.calculator.dto.FinagleTestTypes.{InputListInteger, Result}
import com.twitter.finagle.protobuf.rpc._
import java.util.concurrent.Executors
import com.twitter.finagle.{Name, ListeningServer}
import com.twitter.util.{CountDownLatch, Duration}
import scala.collection.JavaConverters._
import java.net.{InetSocketAddress, SocketAddress}
import com.twitter.finagle.protobuf.rpc.impl.RpcFactoryImpl
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.stats.StatsReceiver
import scala.util.Random
import scala.Some
import com.twitter.finagle.protobuftest.util.{FinagleUtil, JavaUtil, Slf4jStatsReceiver}
import FinagleUtil.{TracerProxy, RpcLatchCallback}
import com.twitter.finagle.protobuftest.util.{JavaUtil, Slf4jStatsReceiver}
import com.twitter.finagle.tracing.Trace
import com.twitter.finagle.protobuf.rpc.channel.ProtobufResponse

object CalculatorFactory {

  val calculatorPort = 10401
  val sumPort = 10402
  val multiplyPort = 10403
  val factorialPort = 10404

  val sleepLatch = new CountDownLatch(1)
  val random = new Random()

  val zipkinHost = "zipkin"

  def createCalculatorServer(port: Int = calculatorPort): ProtobufRpcServer[FinagleCalculator] = {
    new CalculatorServerImpl(port)
  }

  def createClient(address: InetSocketAddress, hostConnectionLimit: Int = 1, retries: Int = 1, version: Int = 1): RpcClient[FinagleCalculator] = {
    new CalculatorClient(address, hostConnectionLimit, retries, version)
  }

  def createRequestBuilder(id: String): CalcRequest.Builder = {
    val builder = CalcRequest.newBuilder
    builder.setId(id)
    builder
  }

  def addInput(builder: CalcRequest.Builder, values: Seq[Long] = Array(7L, 13L)): CalcRequest.Builder = {
    val inputBuilder: InputListInteger.Builder = InputListInteger.newBuilder()
    inputBuilder.addAllValues(JavaUtil.mapToJavaLong(values).toIterable.asJava)
    builder.setInput(inputBuilder)
    builder
  }

  def addRandomInput(builder: CalcRequest.Builder, count: Int = 2, limit: Int = 20): CalcRequest.Builder = {
    val random: Random = new Random
    val inputBuilder: InputListInteger.Builder = InputListInteger.newBuilder()
    for (idx <- 1 to count) {
      inputBuilder.addValues(random.nextInt(limit))
    }
    builder.setInput(inputBuilder)
    builder
  }

  def doSleep() {
    sleepLatch.await(Duration.fromMilliseconds(5 + random.nextInt(20)))
  }

}

sealed trait CalcServiceMethod {
  def ordinal: Int
}

object CalcServiceMethod {

  case class Sum() extends CalcServiceMethodImpl(0)

  case class Multiply() extends CalcServiceMethodImpl(1)

  case class Factorial() extends CalcServiceMethodImpl(2)

  def list(): List[CalcServiceMethod] = {
    Sum() :: Multiply() :: Factorial() :: List()
  }

  def get(index: Int): CalcServiceMethod = {
    val modulus: Int = index % 3
    modulus match {
      case 0 => Sum()
      case 1 => Multiply()
      case 2 => Factorial()
    }
  }

  class CalcServiceMethodImpl(_ordinal: Int) extends CalcServiceMethod {
    override def ordinal: Int = _ordinal
  }

}

class CalculatorService extends FinagleCalculator {

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[CalculatorService])

  val counter = new AtomicInteger()

  override def factorial(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.factorialInput(request.getInput))
    builder.setResult(resultBuilder)

    callback.run(builder.build())
  }

  override def multiply(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.multiplyInput(request.getInput))
    builder.setResult(resultBuilder)

    callback.run(builder.build())
  }

  override def sum(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.sumInput(request.getInput))
    builder.setResult(resultBuilder)

    callback.run(builder.build())
  }

  def createResponseBuilder(request: CalcRequest): CalcResponse.Builder = {
    val builder = CalcResponse.newBuilder()
    builder.setId(request.getId)
  }

  def createResultBuilder(value: Long): Result.Builder = {
    val resultBuilder = Result.newBuilder()
    resultBuilder.setResult(value)
  }

}

class MultiplierService extends FinagleMultiplier {

  import CalculatorFactory.doSleep

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[MultiplierService])

  val counter = new AtomicInteger()

  override def multiply(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.multiplyInput(request.getInput))
    builder.setResult(resultBuilder)

    doSleep()
    callback.run(builder.build())
  }

  def createResponseBuilder(request: CalcRequest): CalcResponse.Builder = {
    val builder = CalcResponse.newBuilder()
    builder.setId(request.getId)
  }

  def createResultBuilder(value: Long): Result.Builder = {
    val resultBuilder = Result.newBuilder()
    resultBuilder.setResult(value)
  }

}

class SummerService extends FinagleSummer {

  import CalculatorFactory.doSleep

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[SummerService])

  val counter = new AtomicInteger()

  override def sum(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.sumInput(request.getInput))
    builder.setResult(resultBuilder)

    doSleep()
    callback.run(builder.build())
  }

  def createResponseBuilder(request: CalcRequest): CalcResponse.Builder = {
    val builder = CalcResponse.newBuilder()
    builder.setId(request.getId)
  }

  def createResultBuilder(value: Long): Result.Builder = {
    val resultBuilder = Result.newBuilder()
    resultBuilder.setResult(value)
  }

}

class FactorialService extends FinagleFactorial {

  import CalculatorFactory.doSleep

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[FactorialService])

  val counter = new AtomicInteger()

  override def factorial(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val builder: CalcResponse.Builder = createResponseBuilder(request)
    val resultBuilder = createResultBuilder(CalculatorUtil.factorialInput(request.getInput))
    builder.setResult(resultBuilder)

    doSleep()
    callback.run(builder.build())
  }

  def createResponseBuilder(request: CalcRequest): CalcResponse.Builder = {
    val builder = CalcResponse.newBuilder()
    builder.setId(request.getId)
  }

  def createResultBuilder(value: Long): Result.Builder = {
    val resultBuilder = Result.newBuilder()
    resultBuilder.setResult(value)
  }

}

class DistributedCalculatorService(factorialClient: FactorialClient, multiplierClient: MultiplierClient, summerClient: SummerClient)
  extends FinagleCalculator {

  private val logger: Logger = LoggerFactory.getLogger(classOf[CalculatorService])

  val counter = new AtomicInteger()

  getClass.getDeclaredClasses

  override def factorial(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val clientController = factorialClient.factory.createController().asInstanceOf[RpcControllerWithOnFailureCallback]
    clientController.onFailure(new RpcFailureCallback("factorial"))
    val clientCallback: RpcLatchCallback[CalcResponse] = FinagleUtil.latchCallback()
    factorialClient.stub.asInstanceOf[FinagleFactorial].factorial(clientController, request, clientCallback)
    clientCallback.latch.await()
    callback.run(clientCallback.result.get)
  }

  override def multiply(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val clientController = multiplierClient.factory.createController().asInstanceOf[RpcControllerWithOnFailureCallback]
    clientController.onFailure(new RpcFailureCallback("multiply"))
    val clientCallback: RpcLatchCallback[CalcResponse] = FinagleUtil.latchCallback()
    multiplierClient.stub.asInstanceOf[FinagleMultiplier].multiply(clientController, request, clientCallback)
    clientCallback.latch.await()
    callback.run(clientCallback.result.get)
  }

  override def sum(controller: RpcController, request: CalcRequest, callback: RpcCallback[CalcResponse]): Unit = {
    val clientController = summerClient.factory.createController().asInstanceOf[RpcControllerWithOnFailureCallback]
    clientController.onFailure(new RpcFailureCallback("summer"))
    val clientCallback: RpcLatchCallback[CalcResponse] = FinagleUtil.latchCallback()
    summerClient.stub.asInstanceOf[FinagleSummer].sum(clientController, request, clientCallback)
    clientCallback.latch.await()
    callback.run(clientCallback.result.get)
  }

  def createResponseBuilder(request: CalcRequest): CalcResponse.Builder = {
    val builder = CalcResponse.newBuilder()
    builder.setId(request.getId)
  }

  def createResultBuilder(value: Long): Result.Builder = {
    val resultBuilder = Result.newBuilder()
    resultBuilder.setResult(value)
  }

  class RpcFailureCallback(msg: String) extends RpcCallback[Throwable] {

    def run(throwable: Throwable) = {
      val exceptionMessage: String = "call failed: " + msg
      logger.debug(exceptionMessage, throwable)
      throw new RuntimeException(exceptionMessage, throwable)
    }
  }

}

trait ProtobufRpcServer[Svc <: ProtobufService] {

  import com.twitter.finagle.zipkin.thrift.ZipkinTracer
  import CalculatorFactory.zipkinHost

  protected val LOGGER: Logger = LoggerFactory.getLogger(classOf[ProtobufRpcServer[Svc]])

  private val executor = Executors.newFixedThreadPool(3, FinagleUtil.threadFactory("rpcTestServer-%d"))
  val receiver: StatsReceiver = Slf4jStatsReceiver().scope("pb-server")
  lazy val zipkinTracer = ZipkinTracer.mk(host = zipkinHost, port = 9410, statsReceiver = receiver, sampleRate = 1)
  val tracer = TracerProxy()

  def setZipkinTracer() = {
    LOGGER.info("setZipkinTracer(): {}", this)
    tracer.self = zipkinTracer
  }

  def serverBuilder = {
    val builder: ServerBuilder[Nothing, Nothing, Nothing, Nothing, Nothing] = ServerBuilder.get().maxConcurrentRequests(10).reportTo(receiver)
      .tracer(tracer)
    builder.asInstanceOf[ServerBuilder[Any, Any, Any, Any, Any]]
  }

  private val serverOption: Option[RpcServer] = {
    val factory: RpcFactory = new RpcFactoryImpl()
    val rpcServer: RpcServer = factory.createServer(serverBuilder, port, createService, new ExceptionHandlerImpl, executor)
    Some(rpcServer)
  }

  def port: Int

  def createService: Svc

  def close(): Unit = {
    def doClose(server: RpcServer) {
      try {
        server.close(Duration.fromMilliseconds(10))
      }
      catch {
        case t: Throwable => LOGGER.warn("failed to close server: " + server, t)
      }
    }

    serverOption map (server => {
      LOGGER.warn("closing server: {}", server)
      doClose(server)
    })
    executor.shutdownNow()
  }

}

class CalculatorServerImpl(private val _port: Int) extends ProtobufRpcServer[FinagleCalculator] {

  override def port: Int = _port

  override def createService: FinagleCalculator = new CalculatorService
}

class FactorialServerImpl(private val _port: Int) extends ProtobufRpcServer[FinagleFactorial] {

  override def port: Int = _port

  override def createService: FinagleFactorial = new FactorialService
}

class MultiplierServerImpl(private val _port: Int) extends ProtobufRpcServer[FinagleMultiplier] {

  override def port: Int = _port

  override def createService: FinagleMultiplier = new MultiplierService
}

class SummerServerImpl(private val _port: Int) extends ProtobufRpcServer[FinagleSummer] {

  override def port: Int = _port

  override def createService: FinagleSummer = new SummerService
}

class DistributedCalculatorServerImpl(private val _port: Int, val factorialClient: FactorialClient, val multiplierClient: MultiplierClient,
  val summerClient: SummerClient) extends ProtobufRpcServer[FinagleCalculator] {

  val clients: List[RpcClient[ProtobufService]] = List(factorialClient.asInstanceOf[RpcClient[ProtobufService]],
    multiplierClient.asInstanceOf[RpcClient[ProtobufService]], summerClient.asInstanceOf[RpcClient[ProtobufService]])

  override def port: Int = _port

  override def createService: FinagleCalculator = new DistributedCalculatorService(factorialClient, multiplierClient, summerClient)

  override def setZipkinTracer(): Unit = {
    super.setZipkinTracer()
    clients map {
      client => client.setZipkinTracer()
    }
  }

  override def close() = {
    super.close()
    clients map {
      client => doClose(client)
    }
  }

  def doClose(client: RpcClient[ProtobufService]) = {
    try {
      client.close()
    }
    catch {
      case t: Throwable => LOGGER.warn("failed to close client: " + client, t)
    }
  }
}

class CalculatorClient(address: InetSocketAddress, hostConnectionLimit: Int = 1, retries: Int = 1, version: Int)
  extends RpcClient[FinagleCalculator](LoggerFactory.getLogger(classOf[CalculatorClient]), address, hostConnectionLimit, retries) {

  override def serviceStub = FinagleCalculator.newStub(null).asInstanceOf[ {def newStub(channel: RpcChannel): FinagleCalculator}]

  override def protocolVersion: Int = version
}

class FactorialClient(address: InetSocketAddress, hostConnectionLimit: Int = 1, retries: Int = 1)
  extends RpcClient[FinagleFactorial](LoggerFactory.getLogger(classOf[FactorialClient]), address, hostConnectionLimit, retries) {

  override def serviceStub = FinagleFactorial.newStub(null).asInstanceOf[ {def newStub(channel: RpcChannel): FinagleFactorial}]
}

class MultiplierClient(address: InetSocketAddress, hostConnectionLimit: Int = 1, retries: Int = 1)
  extends RpcClient[FinagleMultiplier](LoggerFactory.getLogger(classOf[MultiplierClient]), address, hostConnectionLimit, retries) {

  override def serviceStub = FinagleMultiplier.newStub(null).asInstanceOf[ {def newStub(channel: RpcChannel): FinagleMultiplier}]
}

class SummerClient(address: InetSocketAddress, hostConnectionLimit: Int = 1, retries: Int = 1)
  extends RpcClient[FinagleSummer](LoggerFactory.getLogger(classOf[SummerClient]), address, hostConnectionLimit, retries) {

  override def serviceStub = FinagleSummer.newStub(null).asInstanceOf[ {def newStub(channel: RpcChannel): FinagleSummer}]
}

abstract class RpcClient[Svc <: ProtobufService](logger: Logger = LoggerFactory.getLogger(classOf[RpcClient[Svc]]), address: InetSocketAddress,
  hostConnectionLimit: Int = 1, retries: Int = 1) {

  import com.twitter.finagle.zipkin.thrift.ZipkinTracer
  import CalculatorFactory.zipkinHost

  private val rpcFactory: RpcFactory = new RpcFactoryImpl
  private val executor = Executors.newFixedThreadPool(2)

  val receiver: StatsReceiver = Slf4jStatsReceiver().scope("pb-client")
  lazy val zipkinTracer = ZipkinTracer.mk(host = zipkinHost, port = 9410, statsReceiver = receiver, sampleRate = 1)
  val tracer = TracerProxy()

  def setZipkinTracer() = {
    logger.info("setZipkinTracer(): {}", this)
    tracer.self = zipkinTracer
  }

  def clientBuilder: ClientBuilder[Any, Any, Any, Any, Any] = {
    val host: String = String.format("%s:%s", address.getHostName, address.getPort.toString)
    logger.info("creating client: host: {}", host)
    ClientBuilder.get().hosts(host).hostConnectionLimit(hostConnectionLimit).retries(retries).requestTimeout(Duration.fromSeconds(1))
      .reportTo(receiver).tracer(tracer).asInstanceOf[ClientBuilder[Any, Any, Any, Any, Any]]
  }

  private val clientOption: Option[Svc] = {
    Some(createStub())
  }

  def factory: RpcFactory = rpcFactory

  def protocolVersion: Int = 1

  def createStub(): Svc = {
    logger.info("server address: {}", address)
    val serviceStub = this.serviceStub
    val stub = rpcFactory.createStub(clientBuilder, serviceStub, new ExceptionResponseHandlerImpl(), executor, protocolVersion)
    stub
  }

  def serviceStub: {def newStub(channel: RpcChannel): Svc}

  def stub: Svc = {
    clientOption.getOrElse(throw new RuntimeException("no client"))
  }

  def close(): Unit = {
    clientOption map (client => {
      logger.warn("closing client: {}", client)
      rpcFactory.release(client.asInstanceOf[ {def getChannel(): RpcChannel}])
    })
    executor.shutdownNow()
  }

  def getBoundAddress(server: ListeningServer): Name = {
    val members: Set[SocketAddress] = server.members
    Name.bound(members.toIterator.next())
  }

}