package com.twitter.finagle.protobuf.rpc.channel

import com.google.protobuf.Message
import com.twitter.finagle.protobuf.test.Echo.EchoRequest
import org.jboss.netty.buffer.ChannelBuffer
import org.scalatest.FunSuite

class LegacyEncodeDecodeTest extends FunSuite {

  test("v0 encoder encodes and decodes protobuf type") {
    val response = EchoRequest.newBuilder
      .setPhrase("Hello world")
      .build()

    val testMethodA: String = "methodA"
    val methodService = SimpleMethodService(Array(testMethodA, "methodB"))
    val encoder: ProtobufEncoderV0 = new ProtobufEncoderV0(methodService)
    val encoded = encoder.encode(null, null, ProtobufRequest(testMethodA, response, None))
    assert(encoded.isInstanceOf[ChannelBuffer], "Should be a ChannelBuffer")

    val encodedBuffer = encoded.asInstanceOf[ChannelBuffer]
    val expectedLength = 8 + response.toByteArray.length
    assert(encodedBuffer.readableBytes === expectedLength)

    val decoder = new TestDecoder
    val decoded: Object = decoder.decode(null, null, encodedBuffer, methodService)
    val decodedRequest = decoded.asInstanceOf[ProtobufRequest]
    assert(decodedRequest.methodName === testMethodA)

    val decodedResponse = decodedRequest.protobuf.asInstanceOf[EchoRequest]
    assert(decodedResponse.getPhrase === response.getPhrase)
  }

  class TestDecoder extends ServerProtobufDecoderV0 {
    override def getPrototype(methodName: String): Message = {
      EchoRequest.getDefaultInstance
    }
  }
}
