package com.twitter.finagle.protobuf

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, ExecutorService, Executors, TimeUnit}

import com.google.protobuf.{RpcCallback, RpcChannel, RpcController}
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.protobuf.rpc.channel.{ProtobufRequest, ProtobufResponse}
import com.twitter.finagle.protobuf.rpc.impl.RpcFactoryImpl
import com.twitter.finagle.protobuf.rpc.{RpcFactory, RpcServer}
import com.twitter.finagle.protobuf.test.Echo.{EchoRequest, EchoResponse, EchoService}
import com.twitter.finagle.protobuf.testUtil.{TestExceptionResponseHandler, TestServiceExceptionHandler}
import com.twitter.util.{Duration, TimeoutException}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}

class LegacyClientIntegrationTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll {

  implicit val factory = new RpcFactoryImpl

  implicit var executorService: ExecutorService = _

  var server: RpcServer = _

  val port = 10410

  override def beforeAll() {
    executorService = Executors.newSingleThreadExecutor()
  }

  override def afterAll(): Unit = {

    server match {
      case srv: RpcServer => srv.close(Duration.fromSeconds(1))
    }

    executorService match {
      case es: ExecutorService => es.shutdownNow()
    }
  }

  test("legacy clients can make calls to servers") {

    val clientVersion = 0

    server = buildEchoServer(port, new StandardEchoService)

    val client = buildEchoClient(port, clientVersion)

    val controller = factory.createController()

    val request = EchoRequest.newBuilder()
      .setPhrase("Hello world")
      .build

    val callback = new LatchCallback[EchoResponse]

    client.echo(controller, request, callback)

    callback.awaitResponse match {
      case Some(response) => assert(response.getPhrase === "Hello world")
      case None => fail("callback timed out")
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

  def buildEchoClient(port: Int, clientVersion: Int = 1)(implicit factory: RpcFactory, executorService: ExecutorService) = {
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
      new TestExceptionResponseHandler,
      executorService,
      clientVersion,List.empty)

    client
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
