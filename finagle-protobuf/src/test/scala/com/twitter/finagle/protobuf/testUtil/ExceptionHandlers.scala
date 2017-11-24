package com.twitter.finagle.protobuf.testUtil

import com.google.protobuf.Message
import com.twitter.finagle.protobuf.rpc.{ExceptionResponseHandler, ServiceExceptionHandler}
import org.slf4j.LoggerFactory

class TestServiceExceptionHandler extends ServiceExceptionHandler[Message] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def canHandle(exception: RuntimeException): Boolean = false

  override def handle(exception: RuntimeException, message: Message): Message = {
    exception.printStackTrace()
    logger.info("service: handle(): exception: " + exception.getMessage + ", message: " + message)
    message
  }
}

class TestExceptionResponseHandler extends ExceptionResponseHandler[Message] {

  private val logger = LoggerFactory.getLogger(getClass)

  def canHandle(message: Message): Boolean = false

  def handle(message: Message): RuntimeException = {
    logger.info("client: handle(): message: " + message.getClass.getName + " -> " + message)
    null
  }
}
