package com.twitter.finagle.protobuf.rpc.channel

/**
 * Method Service that encodes method names to an integer method code, and looks up method names by the method code.
 *
 **/
trait MethodService {

  def encode(methodName: String): Int

  def lookup(code: Int): String

}
