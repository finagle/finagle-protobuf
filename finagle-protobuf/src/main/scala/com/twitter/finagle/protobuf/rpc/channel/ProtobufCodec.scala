package com.twitter.finagle.protobuf.rpc.channel

import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}
import com.twitter.finagle.tracing.{SpanId, Flags, TraceId}

/**
  * Helper functions for client and server codecs
  */
object ProtobufCodec {

  val VERSION_MAGIC_NUMBER: Int = 2 * 1000 * 1000 * 1000

  private[channel] def createVersionBuffer(version: Int): ChannelBuffer = {
    val buffer = ChannelBuffers.buffer(4)
    buffer.writeInt(VERSION_MAGIC_NUMBER + version)
    buffer
  }

  private[channel] def traceBuffer(traceId: TraceId): ChannelBuffer = {
    val buffer = ChannelBuffers.buffer(32)
    buffer.writeLong(traceId.traceId.toLong)
    buffer.writeLong(traceId.parentId.toLong)
    buffer.writeLong(traceId.spanId.toLong)
    val flags = traceId._sampled match {
      case None =>
        traceId.flags
      case Some(true) =>
        traceId.flags.setFlag(Flags.SamplingKnown | Flags.Sampled)
      case Some(false) =>
        traceId.flags.setFlag(Flags.SamplingKnown)
    }
    buffer.writeLong(flags.toLong)
    buffer
  }

  private[channel] def determineTraceId(buffer: ChannelBuffer): TraceId = {
    val trace64 = buffer.readLong()
    val parent64 = buffer.readLong()
    val span64 = buffer.readLong()
    val flags64 = buffer.readLong()

    val flags = Flags(flags64)
    val sampled = if (flags.isFlagSet(Flags.SamplingKnown)) {
      Some(flags.isFlagSet(Flags.Sampled))
    }
    else None

    val traceSpanId = if (trace64 == parent64) None else Some(SpanId(trace64))
    val parentSpanId = if (parent64 == span64) None else Some(SpanId(parent64))
    val traceId = TraceId(traceSpanId, parentSpanId, SpanId(span64), sampled, flags)
    traceId
  }

}
