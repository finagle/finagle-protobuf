package com.twitter.finagle.protobuf.rpc

import com.google.common.collect.Lists
import com.twitter.finagle.protobuf.rpc.channel.SimpleMethodService
import org.scalatest.FunSuite

class SimpleMethodServiceTest extends FunSuite {
  test("find a valid method") {
    val list = Lists.newArrayList("doThis", "doThat")
    val lookupService = new SimpleMethodService(list)

    val thisCode = lookupService.encode("doThis")
    assert(lookupService.lookup(thisCode) === "doThis")

    val thatCode = lookupService.encode("doThat")
    assert(lookupService.lookup(thatCode) === "doThat")
  }

  test("find collisions for eat, essen, drink") {
    intercept[IllegalArgumentException] {
      new SimpleMethodService(Lists.newArrayList("eat", "essen", "drink")) {
        override def createEncoding(input: String) = input match {
          case "eat" => 1
          case "essen" => 1
          case s: String => s.hashCode()
        }
      }
    }
  }

  test("cannot encode unknown methods") {
    intercept[NoSuchMethodException] {
      val service = new SimpleMethodService(Lists.newArrayList("eat", "drink"))
      service.encode("party")
    }
  }

  test("cannot lookup methods with unknown codes") {
    intercept[NoSuchMethodException] {
      val service = new SimpleMethodService(Lists.newArrayList("eat", "drink"))
      service.lookup(12345)
    }
  }
}

