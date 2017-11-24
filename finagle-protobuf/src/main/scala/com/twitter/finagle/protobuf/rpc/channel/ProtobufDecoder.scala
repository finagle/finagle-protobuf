package com.twitter.finagle.protobuf.rpc.channel

import org.jboss.netty.buffer.{ChannelBuffer, ChannelBufferInputStream}
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelHandlerContext

import com.google.protobuf.Message
import org.slf4j.LoggerFactory
import com.twitter.finagle.tracing.{Trace, TraceId}

/**
  * Decodes protobuf messages on a client. Produces a [[ProtobufResponse]]. See [[ProtobufDecoderV1]] for format.
  */
trait ClientProtobufDecoderV1 extends ClientProtobufDecoderV0 {
}

/**
  * Decodes protobuf messages on a server. Produces a [[ProtobufRequest]]. See [[ProtobufDecoderV1]] for format.
  */
trait ServerProtobufDecoderV1 extends ProtobufDecoderV1 {

  def create(methodName: String, message: Message): ProtobufDTO = ProtobufRequest(methodName, message, None)

}

/**
  * Decodes protobuf messages of the following format.  Produces a [[ProtobufRequest]].
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
trait ProtobufDecoderV1 {

  import ProtobufCodec._

  private val logger = LoggerFactory.getLogger(classOf[ProtobufDecoderV1])

  def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer, methodService: MethodService): Object = {

    // do we have enough bytes to decode the message?
    if (buffer.readableBytes() < 8) {
      return null
    }

    buffer.markReaderIndex()

    val group1 = buffer.readInt()
    val group2 = buffer.readInt()
    logger.trace("groups( {}, {} )", group1, group2)

    if (group2 < 0 && group1 > VERSION_MAGIC_NUMBER) {
      // v1+
      val version = group1 - VERSION_MAGIC_NUMBER
      logger.debug("version: {}", version)
      version match {
        case 1 => decodeV1(buffer, methodService)
        case _ => throw new RuntimeException("invalid message, unknown version: " + version + ", group1: " + group1 + ", group2: " + group2)
      }
    }
    else if (group2 >= 0) {
      decodeV0(group1, group2, buffer, methodService)
    }
    else {
      throw new RuntimeException("invalid message: group1: " + group1 + ", group2: " + group2)
    }
  }

  def decodeV1(buffer: ChannelBuffer, methodService: MethodService): ProtobufRequest = {
    // do we have enough bytes to decode the message?
    try {
      if (buffer.readableBytes() < 40) {
        buffer.resetReaderIndex()
        return null
      }

      val traceIdBuffer: ChannelBuffer = buffer.readBytes(32)
      val methodCode = buffer.readInt()
      val length = buffer.readInt()

      if (buffer.readableBytes() < length) {
        buffer.resetReaderIndex()
        return null
      }

      val traceId: TraceId = determineTraceId(traceIdBuffer)
      val methodName = methodService.lookup(methodCode)
      val prototype = getPrototype(methodName)
      logger.debug("decodeV1(): tracing({}, id: {}, sampled: {}): {}: method: {}: size: {}",
        Array(Trace.isActivelyTracing, Trace.id, Trace.id.sampled.getOrElse("not sampled"), prototype.getClass.getName, methodName, length)
          .asInstanceOf[Array[AnyRef]])

      val msgBuffer = buffer.readBytes(length)
      def message = prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(msgBuffer)).build()

      ProtobufRequest(methodName, message, Some(traceId))
    }
    catch {
      case throwable: Throwable =>
        logger.error("failed to decode V1 message", throwable)
        throw throwable
    }
  }

  def decodeV0(methodCode: Int, length: Int, buffer: ChannelBuffer, methodService: MethodService): ProtobufRequest = {
    // do we have enough bytes to decode the message?
    if (buffer.readableBytes() < length) {
      buffer.resetReaderIndex()
      return null
    }

    val methodName = methodService.lookup(methodCode)
    val prototype = getPrototype(methodName)

    logger.debug("decodeV0(): {}: method: {}, size: {}", Array(prototype.getClass.getName, methodName, length).asInstanceOf[Array[AnyRef]])
    val msgBuffer = buffer.readBytes(length)
    def message = prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(msgBuffer)).build()

    ProtobufRequest(methodName, message, None)
  }

  def getPrototype(methodName: String): Message

}

/**
  * Decodes protobuf messages on a client. Produces a [[ProtobufResponse]]. See [[ProtobufDecoderV0]] for format.
  */
trait ClientProtobufDecoderV0 extends ProtobufDecoderV0 {

  private val logger = LoggerFactory.getLogger(classOf[ClientProtobufDecoderV0])

  def create(methodName: String, message: Message): ProtobufDTO = ProtobufResponse(methodName, message, None)

  override def getLogger = logger
}

/**
  * Decodes protobuf messages on a server. Produces a [[ProtobufRequest]].  See [[ProtobufDecoderV0]] for format.
  */
trait ServerProtobufDecoderV0 extends ProtobufDecoderV0 {

  private val logger = LoggerFactory.getLogger(classOf[ServerProtobufDecoderV0])

  def create(methodName: String, message: Message): ProtobufDTO = ProtobufRequest(methodName, message, None)

  override def getLogger = logger
}

/**
  *
  * Decoder for Version 0 messages, wire format is defined below. Produces a [[ProtobufDTO]] depending on client or server.
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
trait ProtobufDecoderV0 {

  private val logger = LoggerFactory.getLogger(classOf[ProtobufDecoderV0])

  def decode(ctx: ChannelHandlerContext, channel: Channel, buffer: ChannelBuffer, methodService: MethodService): Object = {

    if (buffer.readableBytes() < 8) {
      return null
    }
    buffer.markReaderIndex()

    val methodCode = buffer.readInt()
    val length = buffer.readInt()

    decodeDTO(methodService, buffer, methodCode, length)
  }

  def decodeDTO(methodService: MethodService, buffer: ChannelBuffer, methodCode: Int, length: Int): ProtobufDTO = {
    // do we have enough bytes to decode the message?
    if (buffer.readableBytes() < length) {
      buffer.resetReaderIndex()
      return null
    }

    val methodName = methodService.lookup(methodCode)
    val prototype = getPrototype(methodName)

    getLogger.debug("decoding: {}: method: {}, size: {}", Array(prototype.getClass.getName, methodName, length).asInstanceOf[Array[AnyRef]])
    val msgBuffer = buffer.readBytes(length)
    def message = prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(msgBuffer)).build()

    create(methodName, message)
  }

  def create(methodName: String, message: Message): ProtobufDTO

  def getPrototype(methodName: String): Message

  // provide as def so we get the logger for each subtype
  def getLogger = logger
}