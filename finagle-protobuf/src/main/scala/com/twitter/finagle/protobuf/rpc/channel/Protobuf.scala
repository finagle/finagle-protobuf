package com.twitter.finagle.protobuf.rpc.channel

import com.google.protobuf.Message
import com.twitter.finagle.tracing.TraceId

/**
  * Represents a decoded RPC method request or response.
  */
trait ProtobufDTO {
  def methodName: String

  def protobuf: Message

  def traceId: Option[TraceId]
}

/**
  * A decoded protobuf request
  */
case class ProtobufRequest(_methodName: String, _protobuf: Message, _traceId: Option[TraceId]) extends ProtobufDTO {
  val methodName = _methodName
  val protobuf = _protobuf
  val traceId = _traceId
}

/**
  * A protobuf response
  */
case class ProtobufResponse(_methodName: String, _protobuf: Message, _traceId: Option[TraceId]) extends ProtobufDTO {
  val methodName = _methodName
  val protobuf = _protobuf
  val traceId = _traceId
}
