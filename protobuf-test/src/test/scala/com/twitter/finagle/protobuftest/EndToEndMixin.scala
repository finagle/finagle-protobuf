package com.twitter.finagle.protobuftest

import com.twitter.finagle.tracing.{Tracer, Trace}
import scala.Some
import java.net.InetSocketAddress
import org.slf4j.{LoggerFactory, Logger}
import com.google.protobuf.Service
import com.twitter.finagle.protobuftest.calculator.{RpcClient, ProtobufRpcServer}

trait EndToEndMixin[Svc <: Service] {

  var mixinLogger: Logger = LoggerFactory.getLogger(EndToEndMixin.this.getClass)

  var client: Option[RpcClient[Svc]] = None

  def host: String = "localhost"
  def port: Int

  def setup() {
    mixinLogger.info("setup()")
    clearTracing()
    client = Some(createClient(new InetSocketAddress(host, port)))
  }

  def tearDown() {
    mixinLogger.info("tearDown()")

    if (client.isDefined) {
      try {
        client.get.close()
      }
      catch {
        case t: Throwable => mixinLogger.error("client close failed", t)
      }
    }
  }

  def createClient(address: InetSocketAddress): RpcClient[Svc]

  def clearTracing() {
    Trace.clear()
    mixinLogger.info("tracing cleared")
  }

  def configureTracer(clientTracer: Tracer, serverTracer: Tracer) {
    client.get.tracer.self = clientTracer
    mixinLogger.warn("client tracer: " + client.get.tracer)
  }

}
