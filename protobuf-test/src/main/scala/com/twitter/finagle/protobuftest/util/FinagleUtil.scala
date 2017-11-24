package com.twitter.finagle.protobuftest.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.CountDownLatch
import com.google.protobuf.{RpcCallback, Message}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.slf4j.{LoggerFactory, Logger}
import com.twitter.finagle.tracing._
import scala.Some

object FinagleUtil {

  def threadFactory(format: String) = new ThreadFactoryBuilder().setNameFormat(format).build()

  def latchCallback[M <: Message](latch: CountDownLatch = new CountDownLatch(1)): RpcLatchCallback[M] = new RpcLatchCallback[M](latch)

  class RpcLatchCallback[M <: Message](val latch: CountDownLatch = new CountDownLatch(1)) extends RpcCallback[M] {

    private val LOGGER: Logger = LoggerFactory.getLogger(classOf[RpcLatchCallback[M]])

    val resultRef = new AtomicReference[Option[M]](None)
    val complete = new AtomicBoolean()

    def done: Boolean = complete.get()

    def result: Option[M] = resultRef.get()

    override def run(response: M): Unit = {
      resultRef.set(Some(response))
      complete.set(true)
      LOGGER.info("response: {}", response)
      latch.countDown()
    }
  }

  class TestTracer(name: String, sample: Boolean = true) extends BufferingTracer {
    override def sampleTrace(traceId: TraceId): Option[Boolean] = Some(sample)

    override def toString(): String = {
      val sb: StringBuilder = new StringBuilder(getClass.getSimpleName)
      sb append "(" + name + ")"
      sb append "{ records: [ "
      var first = true
      for (record: Record <- this) {
        if (!first) {
          sb append ", "
        }
        first = false
        sb append record.toString
      }
      sb append " ] }"
      sb.toString()
    }
  }

  object TracerProxy {
    def apply(): TracerProxy = {
      new TracerProxy
    }
  }

  class TracerProxy extends Tracer with Proxy {
    @volatile var self: Tracer = new TestTracer("default")

    def record(record: Record) = self.record(record)

    def sampleTrace(traceId: TraceId) = self.sampleTrace(traceId)
  }

}
