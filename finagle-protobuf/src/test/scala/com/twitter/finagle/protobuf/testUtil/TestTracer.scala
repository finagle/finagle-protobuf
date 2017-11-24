package com.twitter.finagle.protobuf.testUtil

import java.util.concurrent.atomic.AtomicReference

import com.twitter.finagle.tracing.{Record, TraceId, Tracer}

class TestTracer extends Tracer {
   val recordsRef = new AtomicReference[List[Record]](List())

   def records = recordsRef.get

   def sampleTrace(traceId: TraceId) = Some(true)

   def record(record: Record): Unit = {
     recordsRef.set(records :+ record)
   }
 }
