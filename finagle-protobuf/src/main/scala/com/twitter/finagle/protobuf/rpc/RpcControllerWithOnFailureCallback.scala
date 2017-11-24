package com.twitter.finagle.protobuf.rpc

import com.google.protobuf.RpcCallback
import com.google.protobuf.RpcController
import com.twitter.finagle.TimeoutException
import com.twitter.finagle.ChannelClosedException

/**
  * A controller which adapts failures to callback results. Following the protobuf interfaces, the RpcController
  * handles failures and cancellation, while the RpcCallback handles successes. This allows us to add a second
  * callback (RpcCallback[Throwable]) to call when a failure occurs. See RpcChannelImpl sources for details.
  */
// todo can we adapt this more directly?
class RpcControllerWithOnFailureCallback extends RpcController {

  private var cancelRequested = false
  private var callback: Option[RpcCallback[Throwable]] = None

  def reset(): Unit = {
    cancelRequested = false
  }

  def failed(): Boolean = {
    throw new RuntimeException("Not implemented")
  }

  def errorText(): String = {
    throw new RuntimeException("Not implemented")
  }

  def startCancel(): Unit = {
    cancelRequested = true
  }

  def setFailed(reason: String): Unit = {
    throw new RuntimeException("Not implemented")
  }

  def setFailed(e: Throwable): Unit = {
    val theCallback: RpcCallback[Throwable] = callback
      .getOrElse(throw new RuntimeException("no 'on failure' callback defined, call failed: " + e.getMessage, e))
    theCallback.run(adapt(e))
  }

  def isCanceled = cancelRequested

  def notifyOnCancel(callback: RpcCallback[Object]): Unit = {
    throw new RuntimeException("Not implemented")
  }

  def onFailure(callback: RpcCallback[Throwable]): RpcControllerWithOnFailureCallback = {
    this.callback = Some(callback)
    this
  }

  def adapt(e: Throwable): Throwable = {
    e match {
      case _: TimeoutException =>
        def wrapped = new java.util.concurrent.TimeoutException(e.getMessage)
        wrapped.initCause(e)
        wrapped
      case _: ChannelClosedException => new RuntimeException(e)
      case _ => e
    }
  }
}
