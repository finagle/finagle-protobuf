package com.twitter.finagle.protobuf.rpc.channel

import com.google.protobuf.Message
import com.twitter.finagle.protobuf.test.Echo.{EchoRequest, EchoResponse}
import com.twitter.finagle.protobuf.testUtil.TestTracer
import com.twitter.finagle.tracing.{Record, Trace, TraceId, Tracer}
import org.jboss.netty.buffer.ChannelBuffer
import org.scalatest.{BeforeAndAfter, FunSuite}

class EncodeDecodeTest extends FunSuite with BeforeAndAfter {

  test("encode and decode a protobuf request") {
    val tracer = new TestTracer
    Trace.letTracerAndNextId(tracer) {
      val traceId = Trace.id

      val protobuf = EchoRequest.newBuilder
        .setPhrase("Hello world")
        .build

      val methodService = SimpleMethodService(Array("methodA", "methodB"))
      val encoder = new ProtobufEncoderV1(methodService)
      val encoded = encoder.encode(null, null, ProtobufRequest("methodA", protobuf, None))
      assert(encoded.isInstanceOf[ChannelBuffer], "should be a ChannelBuffer")

      val encodedBuffer = encoded.asInstanceOf[ChannelBuffer]
      val expectedLength = 48 + protobuf.toByteArray.length
      assert(encodedBuffer.readableBytes === expectedLength)

      val decoder = new ProtobufDecoderV1 {
        override def getPrototype(methodName: String): Message = EchoRequest.getDefaultInstance
      }
      val decoded = decoder.decode(null, null, encodedBuffer, methodService)
      assert(decoded.isInstanceOf[ProtobufRequest], "should be a ProtobufRequest")

      val decodedRequest = decoded.asInstanceOf[ProtobufRequest]
      assert(decodedRequest.methodName === "methodA")
      assert(decodedRequest.traceId.get === traceId)

      val decodedProtobuf = decodedRequest.protobuf.asInstanceOf[EchoRequest]
      assert(decodedProtobuf.getPhrase === "Hello world")
    }
  }

  test("encode and decode a protobuf response") {
    val tracer = new TestTracer
    Trace.letTracerAndNextId(tracer) {
      val traceId = Trace.id

      val protobuf = EchoResponse.newBuilder
        .setPhrase("Hello world")
        .build

      val methodService = SimpleMethodService(Array("methodA", "methodB"))
      val encoder = new ProtobufEncoderV1(methodService)
      val encoded = encoder.encode(null, null, ProtobufRequest("methodA", protobuf, None))
      assert(encoded.isInstanceOf[ChannelBuffer], "should be a ChannelBuffer")

      val encodedBuffer = encoded.asInstanceOf[ChannelBuffer]
      val expectedLength = 48 + protobuf.toByteArray.length
      assert(encodedBuffer.readableBytes === expectedLength)

      val decoder = new ProtobufDecoderV1 {
        override def getPrototype(methodName: String): Message = EchoResponse.getDefaultInstance
      }
      val decoded = decoder.decode(null, null, encodedBuffer, methodService)
      assert(decoded.isInstanceOf[ProtobufRequest], "should be a ProtobufRequest")

      val decodedRequest = decoded.asInstanceOf[ProtobufRequest]
      assert(decodedRequest.methodName === "methodA")
      assert(decodedRequest.traceId.get === traceId)

      val decodedProtobuf = decodedRequest.protobuf.asInstanceOf[EchoResponse]
      assert(decodedProtobuf.getPhrase === "Hello world")
    }
  }

  test("why can't it use a ProtobufRequest?") {
    /*
     * Using a ProtobufResponse fails because the encoder explicitly checks for
     * an instance of ProtobufRequest and silently does the wrong thing if it
     * doesn't get one, i.e. it will just return the msg argument untouched.
     *
     * That seems wrong, but also begs the question about why there are two
     * identical case classes, e.g. ProtobufRequest and ProtobufResponse.
     */
    pendingUntilFixed {
      val tracer = new TestTracer
      Trace.letTracerAndNextId(tracer) {
        val traceId = Trace.id

        val protobuf = EchoResponse.newBuilder
          .setPhrase("Hello world")
          .build

        val methodService = SimpleMethodService(Array("methodA", "methodB"))
        val encoder = new ProtobufEncoderV1(methodService)
        val encoded = encoder.encode(null, null, ProtobufResponse("methodA", protobuf, None))
        assert(encoded.isInstanceOf[ChannelBuffer], "should be a ChannelBuffer")

        val encodedBuffer = encoded.asInstanceOf[ChannelBuffer]
        val expectedLength = 48 + protobuf.toByteArray.length
        assert(encodedBuffer.readableBytes === expectedLength)

        val decoder = new ProtobufDecoderV1 {
          override def getPrototype(methodName: String): Message = EchoResponse.getDefaultInstance
        }
        val decoded = decoder.decode(null, null, encodedBuffer, methodService)
        assert(decoded.isInstanceOf[ProtobufResponse], "should be a ProtobufRequest")

        val decodedRequest = decoded.asInstanceOf[ProtobufResponse]
        assert(decodedRequest.methodName === "methodA")
        assert(decodedRequest.traceId.get === traceId)

        val decodedProtobuf = decodedRequest.protobuf.asInstanceOf[EchoResponse]
        assert(decodedProtobuf.getPhrase === "Hello world")
      }
    }
  }
}
