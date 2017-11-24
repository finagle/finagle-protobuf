package com.twitter.finagle.protobuf

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, ExecutorService, Executors, TimeUnit}

import com.google.protobuf.{Message, RpcCallback, RpcChannel, RpcController}
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufResponse, ProtobufRequest}
import com.twitter.finagle.protobuf.rpc.impl.RpcFactoryImpl
import com.twitter.finagle.protobuf.rpc.{ExceptionResponseHandler, RpcControllerWithOnFailureCallback, RpcFactory, RpcServer}
import com.twitter.finagle.protobuf.test.Echo.{EchoRequest, EchoResponse, EchoService}
import com.twitter.finagle.protobuf.testUtil.TestServiceExceptionHandler
import com.twitter.util.Duration
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class ErrorHandlingIntegrationTest extends FunSuite with BeforeAndAfterAll {

  implicit val factory = new RpcFactoryImpl

  implicit var executorService: ExecutorService = _

  var server: RpcServer = _

  val port = 10460

  override def beforeAll() {
    executorService = Executors.newSingleThreadExecutor()

    server = buildEchoServer(port, new StandardEchoService)
  }

  override def afterAll() {
    server match {
      case s: RpcServer => s.close(Duration.fromSeconds(1))
    }

    executorService match {
      case es: ExecutorService => es.shutdownNow()
    }
  }

  test("fails callback when ExceptionHandler#canHandle blows up") {
    val expectedException = new RuntimeException("Ka-BOOM!")

    val exceptionHandler = new TestExceptionHandler {
      override def canHandle(message: Message) = throw expectedException
    }

    val client = buildEchoClient(port, exceptionHandler)

    val controller = factory.createController().asInstanceOf[RpcControllerWithOnFailureCallback]

    val request = EchoRequest.newBuilder()
      .setPhrase("5... 4... 3... 2... 1... BOOM!")
      .build

    val callback = new LatchCallback[EchoResponse]

    val failureCallback = new LatchCallback[Throwable]

    controller.onFailure(failureCallback)

    client.echo(controller, request, callback)

    callback.awaitResponse match {
      case Some(response) => fail("should have failed")
      case None => "OK"
    }

    failureCallback.awaitResponse match {
      case Some(thrown: Throwable) => assert(thrown.getCause === expectedException)
      case None => fail("should have a throwable")
    }
  }

  test("fails callback when ExceptionHandler#handle blows up") {
    val expectedException = new RuntimeException("Ka-BOOM!")

    val exceptionHandler = new TestExceptionHandler {
      override def handle(message: Message) = throw expectedException
    }

    val client = buildEchoClient(port, exceptionHandler)

    val controller = factory.createController().asInstanceOf[RpcControllerWithOnFailureCallback]

    val request = EchoRequest.newBuilder()
      .setPhrase("5... 4... 3... 2... 1... BOOM!")
      .build

    val callback = new LatchCallback[EchoResponse]

    val failureCallback = new LatchCallback[Throwable]

    controller.onFailure(failureCallback)

    client.echo(controller, request, callback)

    callback.awaitResponse match {
      case Some(response) => fail("should have failed")
      case None => "OK"
    }

    failureCallback.awaitResponse match {
      case Some(thrown: Throwable) => assert(thrown.getCause === expectedException)
      case None => fail("should have a throwable")
    }
  }

  def buildEchoServer(port: Int, impl: EchoService.Interface)(implicit factory: RpcFactory, executorService: ExecutorService) = {
    val serverBuilder = ServerBuilder()
      .maxConcurrentRequests(10)
      .asInstanceOf[ServerBuilder[ProtobufRequest, ProtobufResponse, Any, Any, Any]]

    val server = factory.createServer(
      serverBuilder,
      port,
      EchoService.newReflectiveService(impl),
      new TestServiceExceptionHandler,
      executorService)

    server
  }

  def buildEchoClient(port: Int, handler: ExceptionResponseHandler[Message])(implicit factory: RpcFactory, executorService: ExecutorService) = {
    val clientBuilder = ClientBuilder()
      .hosts("localhost:%d".format(port))
      .hostConnectionLimit(1)
      .retries(1)
      .requestTimeout(Duration.fromSeconds(1))
      .asInstanceOf[ClientBuilder[Any, Any, Any, Any, Any]]

    val serviceStub = EchoService.newStub(null)
      .asInstanceOf[ {def newStub(channel: RpcChannel): EchoService}]

    val client = factory.createStub(
      clientBuilder,
      serviceStub,
      handler,
      executorService,
      1,
      List.empty)

    client
  }

  class TestExceptionHandler extends ExceptionResponseHandler[Message] {
    override def canHandle(message: Message) = true

    override def handle(message: Message) = new RuntimeException("wut")
  }

  class StandardEchoService extends EchoService.Interface {
    def echo(controller: RpcController, request: EchoRequest, callback: RpcCallback[EchoResponse]) {
      val response = EchoResponse.newBuilder
        .setPhrase(request.getPhrase)
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
        case false => None
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
