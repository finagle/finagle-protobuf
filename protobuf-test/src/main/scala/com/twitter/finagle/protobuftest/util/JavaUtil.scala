package com.twitter.finagle.protobuftest.util

import java.lang

object JavaUtil {

  def mapToJavaLong(input: Seq[Long]): Seq[lang.Long] = {
    input map {
      in => in.asInstanceOf[lang.Long]
    }
  }

  def mapToScalaLong(input: Seq[lang.Long]): Seq[Long] = {
    input map {
      in => in.asInstanceOf[Long]
    }
  }
}
