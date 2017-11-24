package com.twitter.finagle.protobuf.rpc

import com.google.protobuf.Message

/**
 * If the invocation of a service causes a RuntimeException this handler serializes it into a response message
 * that can be deserialized by an [[ExceptionResponseHandler]].
 */
trait ServiceExceptionHandler[T] {

  def canHandle(exception: RuntimeException): Boolean

  def handle(exception: RuntimeException, message: Message): T

}