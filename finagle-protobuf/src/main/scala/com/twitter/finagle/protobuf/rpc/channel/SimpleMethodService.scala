package com.twitter.finagle.protobuf.rpc.channel

import scala.collection.JavaConversions._
import scala.collection.mutable._
import scala.collection.mutable
import com.google.protobuf.Descriptors.MethodDescriptor
import scala.collection.JavaConverters._

private[rpc] object SimpleMethodService {

  def apply(rpcService: com.google.protobuf.Descriptors.ServiceDescriptor): SimpleMethodService = {
    new SimpleMethodService(extractMethodNames(rpcService))
  }

  def apply(methodNames: Seq[String]): SimpleMethodService = {
    new SimpleMethodService(seqAsJavaList(methodNames))
  }

  def extractMethodNames(serviceDescriptor: com.google.protobuf.Descriptors.ServiceDescriptor): List[String] = {
    val descriptors: mutable.Buffer[MethodDescriptor] = serviceDescriptor.getMethods.asScala
    val methodNames: mutable.Buffer[String] = descriptors.map {
      methodDescriptor => methodDescriptor.getName
    }
    methodNames.toList
  }

}

/**
 * Maps method names to short codes. This particular implementation uses the trivial String.hashCode.
 */
private[rpc] class SimpleMethodService(val methods: java.util.List[String]) extends MethodService {

  private val codeToNameMulti = new HashMap[Int, Set[String]] with MultiMap[Int, String]
  methods.toList foreach {
    method => codeToNameMulti.getOrElseUpdate(createEncoding(method), Set.empty[String]) += method
  }

  // do we have collisions?
  codeToNameMulti foreach {
    codeAndNameTuple =>
      if (codeAndNameTuple._2.size > 1) {
        val ms = codeAndNameTuple._2.map{ s => s + "()" }.mkString(", ")
        throw new IllegalArgumentException("Collision on methods: " + ms)
      }
  }

  private val codeToName = new HashMap[Int, String]
  codeToNameMulti foreach {
    tuple => codeToName(tuple._1) = tuple._2.iterator.next()
  }

  private val nameToCode = codeToName map {
    _.swap
  }

  def encode(methodName: String): Int = nameToCode
    .getOrElse(methodName, throw new NoSuchMethodException("cannot generate code for unknown method: " + methodName + "()"))

  def lookup(code: Int): String = codeToName.getOrElse(code, throw new NoSuchMethodException("no method found for code: (" + code + ")"))

  def createEncoding(string: String): Int = {
    string.hashCode()
  }

}
