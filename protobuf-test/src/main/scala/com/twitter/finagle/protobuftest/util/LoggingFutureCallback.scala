package com.twitter.finagle.protobuftest.util

import com.google.common.util.concurrent.FutureCallback
import org.slf4j.{LoggerFactory, Logger}

object LoggingFutureCallback {

  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[LoggingFutureCallback[_]])
}

class LoggingFutureCallback[M] extends FutureCallback[M] {

  import LoggingFutureCallback._

  override def onFailure(throwable: Throwable): Unit = {
    LOGGER.info("FutureCallback onFailure: {}", throwable)
  }

  override def onSuccess(result: M): Unit = {
    LOGGER.info("FutureCallback onSuccess: {}", result)
  }
}

