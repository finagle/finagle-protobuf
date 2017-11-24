package com.twitter.finagle.protobuf.rpc

import com.twitter.util.Duration

/**
  * A server with a close
  */
trait RpcServer {

  def close(d: Duration): Unit

}
