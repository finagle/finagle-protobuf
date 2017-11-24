package com.twitter.finagle.protobuf.rpc.channel

import org.jboss.netty.handler.codec.oneone.OneToOneEncoder
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.Channel
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}

import com.google.protobuf.Message
import org.slf4j.LoggerFactory
import com.twitter.finagle.tracing.Trace

/**
  * Encodes a [[ProtobufRequest]] into a ChannelBuffer.
  */
object ProtobufEncoderV1 {

  import ProtobufCodec._

  private val logger = LoggerFactory.getLogger(classOf[ProtobufEncoderV1])

  val VERSION: Int = 1

  private[channel] def versionBuffer: ChannelBuffer = {
    createVersionBuffer(VERSION)
  }

}

/**
  * Encodes a [[ProtobufDTO]] into a ChannelBuffer.
  */
object ProtobufEncoderV0 {

  private val logger = LoggerFactory.getLogger(classOf[ProtobufEncoderV0])

  private[channel] def msgLengthBuffer(messageBytes: Array[Byte]): ChannelBuffer = {
    val msgLenBuf = ChannelBuffers.buffer(4)
    msgLenBuf.writeInt(messageBytes.length)
    msgLenBuf
  }

  private[channel] def methodNameBuffer(methodNameCode: Int): ChannelBuffer = {
    val methodNameBuf = ChannelBuffers.buffer(4)
    methodNameBuf.writeInt(methodNameCode)
    methodNameBuf
  }

}

/**
 * Encodes a [[ProtobufRequest]] into a ChannelBuffer.
 *
 * Message Format
 *
 *  - version (4 bytes): '1'
 *  - not V0 marker (4 bytes): any negative value to distinguish this message from a V0 message
 *  - trace span id (8 bytes): trace span id from TraceId.traceId
 *  - span id (8 bytes): span id from TraceId.spanId
 *  - parent span id (8 bytes): parent span id from TraceId.parentId
 *  - trace status (8 bytes): encoded trace id flags
 *  - method code (4 bytes): integer representing the method called
 *  - message length (4 bytes): the length of the protobuf message
 *  - protobuf bytes (variable, specified in 'message length'): bytes of the protobuf message
 *
 */
/*
* Offset:
* 0             4                8              16               24             32              40            44               48
* +-------------|----------------|---------------|---------------|----------------|--------------|-------------|----------------|----------------+
* | version     | not V0 marker  | trace span id | span id       | parent span id | trace status | method code | message length | protobuf bytes |
* +-------------|----------------|---------------|---------------|----------------|--------------|-------------|----------------|----------------+
*/
class ProtobufEncoderV1(val methodService: MethodService) extends OneToOneEncoder {

  import ProtobufEncoderV1._
  import ProtobufCodec._

  @throws(classOf[Exception])
  def encode(ctx: ChannelHandlerContext, channel: Channel, msg: Object): Object = {

    if (!msg.isInstanceOf[ProtobufRequest]) {
      return msg
    }

    val request = msg.asInstanceOf[ProtobufRequest]
    val methodNameCode = methodService.encode(request.methodName)
    val messageBytes = request.protobuf.asInstanceOf[Message].toByteArray

    logger.debug("encode: V1: tracing({}, id: {}, sampled: {}): {}: method: {}: length: {}",
      Array(Trace.isActivelyTracing, Trace.id, Trace.id.sampled.getOrElse("not sampled"), request.protobuf.getClass.getName, request.methodName,
        messageBytes.length).asInstanceOf[Array[AnyRef]])
    logger.trace("message: {}", request.protobuf)

    ChannelBuffers.wrappedBuffer(versionBuffer, notV0Buffer, traceBuffer(Trace.id), ProtobufEncoderV0.methodNameBuffer(methodNameCode),
      ProtobufEncoderV0.msgLengthBuffer(messageBytes), ChannelBuffers.wrappedBuffer(messageBytes))
  }

  private[channel] def notV0Buffer: ChannelBuffer = {
    val buffer = ChannelBuffers.buffer(4)
    buffer.writeInt(-1)
    buffer
  }

}

/**
 * Encodes a [[ProtobufDTO]] into a ChannelBuffer.
 *
 * Message Format
 *
 *  - method code (4 bytes): integer representing the method called
 *  - message length (4 bytes): the length of the protobuf message
 *  - protobuf bytes (variable, specified in 'message length'): bytes of the protobuf message
 */
/*
* Offset: 0             4                8
* +-------------+----------------+------------------+
* | method code | message length | protobuf message |
* +-------------+----------------+------------------+
*/
class ProtobufEncoderV0(val methodService: MethodService) extends OneToOneEncoder {

  import ProtobufEncoderV0._

  @throws(classOf[Exception])
  def encode(ctx: ChannelHandlerContext, channel: Channel, msg: Object): Object = {

    if (!msg.isInstanceOf[ProtobufDTO]) {
      return msg
    }

    val request = msg.asInstanceOf[ProtobufDTO]
    val methodNameCode = methodService.encode(request.methodName)
    val messageBytes = request.protobuf.toByteArray

    logger.debug("encode: V0: tracing({}, id: {}, sampled: {}): {}: method: {}: length: {}",
      Array(Trace.isActivelyTracing, Trace.id, Trace.id.sampled.getOrElse("not sampled"), request.protobuf.getClass.getName, request.methodName,
        messageBytes.length).asInstanceOf[Array[AnyRef]])
    logger.trace("message: {}", request.protobuf)

    ChannelBuffers.wrappedBuffer(methodNameBuffer(methodNameCode), msgLengthBuffer(messageBytes), ChannelBuffers.wrappedBuffer(messageBytes))
  }

}
