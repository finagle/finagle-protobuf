package com.twitter.finagle.protobuftest.calculator

import com.twitter.finagle.protobuf.rpc.{ExceptionResponseHandler, ServiceExceptionHandler}
import com.google.protobuf.Message
import org.slf4j.{LoggerFactory, Logger}

class ExceptionHandlerImpl extends ServiceExceptionHandler[Message] {

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[ExceptionHandlerImpl])

  override def canHandle(exception: RuntimeException): Boolean = {
    LOGGER.trace("service: canHandle(): exception: " + exception.getMessage)
    false
  }

  override def handle(exception: RuntimeException, message: Message): Message = {
    exception.printStackTrace()
    LOGGER.info("service: handle(): exception: " + exception.getMessage + ", message: " + message)
    message
  }
}

class ExceptionResponseHandlerImpl extends ExceptionResponseHandler[Message] {

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[ExceptionResponseHandlerImpl])

  def canHandle(message: Message): Boolean = {
    LOGGER.trace("client: canHandle(): message: " + message.getClass.getName + " -> " + message)
    map(message) != null
  }

  def handle(message: Message): RuntimeException = {
    LOGGER.info("client: handle(): message: " + message.getClass.getName + " -> " + message)
    map(message)
  }

  private def map(message: Message): RuntimeException = {
    null
  }
}