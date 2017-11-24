package com.twitter.finagle.protobuf

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, ExecutorService, Executors, TimeUnit}

import com.google.protobuf.{RpcCallback, RpcChannel, RpcController}
import com.twitter.finagle.{SimpleFilter, Service}
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest}
import com.twitter.finagle.protobuf.rpc.impl.RpcFactoryImpl
import com.twitter.finagle.protobuf.rpc.{RpcFactory, RpcServer}
import com.twitter.finagle.protobuf.test.Echo.{EchoRequest, EchoResponse, EchoService}
import com.twitter.finagle.protobuf.testUtil.{TestTracer, TestExceptionResponseHandler, TestServiceExceptionHandler}
import com.twitter.finagle.tracing.{Record, TraceId, Tracer}
import com.twitter.util.{Futures, Future, Duration, TimeoutException}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}

class DistributedIntegrationTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll {

  implicit val factory = new RpcFactoryImpl

  implicit var executorService: ExecutorService = null

  var reverseServer: RpcServer = null
  var foobarServer: RpcServer = null
  var distributedServer: RpcServer = null

  val reverseServerPort = 10410
  val foobarServerPort = 10420
  val distributedServerPort = 10430

  before {
    executorService = Executors.newFixedThreadPool(4)
  }

  after {
    val servers = Seq(reverseServer, foobarServer, distributedServer)

    servers foreach {
      case server: RpcServer => server.close(Duration.fromSeconds(1))
    }

    executorService match {
      case es: ExecutorService => es.shutdownNow()
    }
  }

  test("makes and traces distributed rpc calls") {

    implicit val tracer = new TestTracer

    val reverseEchoClient = buildEchoClient(reverseServerPort)

    val foobarEchoClient = buildEchoClient(foobarServerPort)

    val distributedEchoClient = buildEchoClient(distributedServerPort)

    reverseServer = buildEchoServer(reverseServerPort, new ReverseEchoService)

    foobarServer = buildEchoServer(foobarServerPort, new FoobarEchoService)

    distributedServer = buildEchoServer(distributedServerPort, new DistributedEchoService(reverseEchoClient, foobarEchoClient))

    val controller = factory.createController()

    val request = EchoRequest.newBuilder()
      .setPhrase("Hello world")
      .build

    val callback = new LatchCallback[EchoResponse]

    distributedEchoClient.echo(controller, request, callback)

    callback.awaitResponse match {
    case Some(response) => assert(response.getPhrase === "dlrow olleH Hello world FOOBAR!")
    case None => fail("callback timed out")
    }

    assert(tracer.records.nonEmpty, "should have traced something")
  }

  test("applies client filters") {

    implicit val tracer = new TestTracer


    val filterMessage1: String = "ENHANCED BY FILTER1"

    val echoFilter1 = buildFilter(filterMessage1)

    val filterMessage2: String = "ENHANCED BY FILTER2"

    val echoFilter2 = buildFilter(filterMessage2)

    val client = buildEchoClient(foobarServerPort, echoFilter1, echoFilter2)


    foobarServer = buildEchoServer(foobarServerPort, new FoobarEchoService )


    val controller = factory.createController()

    val request = EchoRequest.newBuilder()
      .setPhrase("Hello world")
      .build

    val callback = new LatchCallback[EchoResponse]

    client.echo(controller, request, callback)

    callback.awaitResponse match {
    case Some(response) => assert(response.getPhrase === s"Hello world $filterMessage1 $filterMessage2 FOOBAR!")
    case None => fail("callback timed out")
    }

    assert(tracer.records.nonEmpty, "should have traced something")
  }

  def buildFilter(msg:String): SimpleFilter[ProtobufRequest, ProtobufResponse] {def apply(request: ProtobufRequest, service: Service[ProtobufRequest, ProtobufResponse]): Future[ProtobufResponse]} = {
    new SimpleFilter[ProtobufRequest, ProtobufResponse] {
      override def apply(request: ProtobufRequest, service: Service[ProtobufRequest, ProtobufResponse]): Future[ProtobufResponse] = {
        val origRequestMessage: EchoRequest = request.protobuf.asInstanceOf[EchoRequest]

        val newRequestMessage = EchoRequest.newBuilder(origRequestMessage)
          .setPhrase(origRequestMessage.getPhrase + " " + msg).build()

        val newRequest = ProtobufRequest(request._methodName, newRequestMessage, request._traceId)

        service(newRequest)
      }
    }
  }

  test("applies server filters") {

    implicit val tracer = new TestTracer

    val client = buildEchoClient(foobarServerPort)


    val echoFilter1 = new SimpleFilter[ProtobufRequest,ProtobufResponse] {
      override def apply(request: ProtobufRequest, service: Service[ProtobufRequest, ProtobufResponse]): Future[ProtobufResponse] = {
        val origRequestMessage: EchoRequest = request.protobuf.asInstanceOf[EchoRequest]

        val newRequestMessage = EchoRequest.newBuilder(origRequestMessage)
          .setPhrase(origRequestMessage.getPhrase+" ENHANCED BY FILTER1").build()

        val newRequest = ProtobufRequest(request._methodName,newRequestMessage,request._traceId)

        service(newRequest)
      }
    }

    val echoFilter2 = new SimpleFilter[ProtobufRequest,ProtobufResponse] {
      override def apply(request: ProtobufRequest, service: Service[ProtobufRequest, ProtobufResponse]): Future[ProtobufResponse] = {
        val origRequestMessage: EchoRequest = request.protobuf.asInstanceOf[EchoRequest]

        val newRequestMessage = EchoRequest.newBuilder(origRequestMessage)
          .setPhrase(origRequestMessage.getPhrase+" AND BY FILTER2").build()

        val newRequest = ProtobufRequest(request._methodName,newRequestMessage,request._traceId)

        service(newRequest)
      }
    }

    foobarServer = buildEchoServer(foobarServerPort, new FoobarEchoService, echoFilter1,echoFilter2)


    val controller = factory.createController()

    val request = EchoRequest.newBuilder()
      .setPhrase("Hello world")
      .build

    val callback = new LatchCallback[EchoResponse]

    client.echo(controller, request, callback)

    callback.awaitResponse match {
      case Some(response) => assert(response.getPhrase === "Hello world ENHANCED BY FILTER1 AND BY FILTER2 FOOBAR!")
      case None => fail("callback timed out")
    }

    assert(tracer.records.nonEmpty, "should have traced something")
  }



  def buildEchoServer(port: Int, impl: EchoService.Interface,filters:SimpleFilter[ProtobufRequest,ProtobufResponse]*)(implicit factory: RpcFactory, executorService: ExecutorService, tracer: Tracer) = {
    val serverBuilder = ServerBuilder()
      .maxConcurrentRequests(10)
      .tracer(tracer)
      .asInstanceOf[ServerBuilder[ProtobufRequest, ProtobufResponse, Any, Any, Any]]

    val server = factory.createServer(
      serverBuilder,
      port,
      EchoService.newReflectiveService(impl),
      new TestServiceExceptionHandler,
      executorService, filters.toList)

    server
  }

  def buildEchoClient(port: Int, filters:SimpleFilter[ProtobufRequest,ProtobufResponse]*)(implicit factory: RpcFactory, executorService: ExecutorService, tracer: Tracer) = {
    val clientBuilder = ClientBuilder()
      .hosts("localhost:%d".format(port))
      .hostConnectionLimit(1)
      .retries(1)
      .requestTimeout(Duration.fromSeconds(1))
      .tracer(tracer)
      .asInstanceOf[ClientBuilder[Any, Any, Any, Any, Any]]

    val serviceStub = EchoService.newStub(null)
      .asInstanceOf[ {def newStub(channel: RpcChannel): EchoService}]

    val client = factory.createStub(
      clientBuilder,
      serviceStub,
      new TestExceptionResponseHandler,
      executorService,
      1, filters.toList)

    client
  }

  class DistributedEchoService(firstClient: EchoService, secondClient: EchoService) extends EchoService.Interface {
    def echo(controller: RpcController, request: EchoRequest, callback: RpcCallback[EchoResponse]) {
      val combined = for {
        cb1 <- makeCall(firstClient, request.getPhrase)
        cb2 <- makeCall(secondClient, request.getPhrase)
      } yield cb1.getPhrase + " " + cb2.getPhrase

      val combinedPhrase = combined.awaitResponse match {
        case Some(phrase) => phrase
        case None => "Expected a combined phrase, but didn't get one"
      }

      val response = EchoResponse.newBuilder
        .setPhrase(combinedPhrase)
        .build

      callback.run(response)
    }

    private def makeCall(client: EchoService, phrase: String) = {
      val controller = factory.createController()
      val callback = new LatchCallback[EchoResponse]
      val request = EchoRequest.newBuilder.setPhrase(phrase).build
      client.echo(controller, request, callback)
      callback
    }
  }

  class ReverseEchoService extends EchoService.Interface {
    def echo(controller: RpcController, request: EchoRequest, callback: RpcCallback[EchoResponse]) {
      val response = EchoResponse.newBuilder
        .setPhrase(request.getPhrase.reverse)
        .build
      callback.run(response)
    }
  }

  class FoobarEchoService extends EchoService.Interface {
    def echo(controller: RpcController, request: EchoRequest, callback: RpcCallback[EchoResponse]) {
      val response = EchoResponse.newBuilder
        .setPhrase(request.getPhrase + " FOOBAR!")
        .build
      callback.run(response)
    }
  }

  class LatchCallback[T] extends RpcCallback[T] {

    private val ref = new AtomicReference[Option[T]](None)

    private val latch = new CountDownLatch(1)

    override def run(response: T) {
      ref.set(Some(response))
      latch.countDown()
    }

    def map[U](f: (T) => U): LatchCallback[U] = {
      awaitResponse match {
        case Some(t) => LatchCallback(f(t))
        case None => new LatchCallback[U]
      }
    }

    def flatMap[U](f: (T) => LatchCallback[U]): LatchCallback[U] = {
      awaitResponse match {
        case Some(t) => f(t)
        case None => new LatchCallback[U]
      }
    }

    def awaitResponse: Option[T] = awaitResponse(Duration.fromSeconds(1))

    def awaitResponse(timeout: Duration): Option[T] = {
      latch.await(timeout.inMilliseconds, TimeUnit.MILLISECONDS) match {
        case true => ref.get
        case false => throw new TimeoutException("Timeout")
      }
    }
  }

  object LatchCallback {
    def apply[T](t: T): LatchCallback[T] = {
      val cb = new LatchCallback[T]
      cb.run(t)
      cb
    }
  }
}
