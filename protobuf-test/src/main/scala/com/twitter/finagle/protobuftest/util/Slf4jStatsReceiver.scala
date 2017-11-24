package com.twitter.finagle.protobuftest.util

import collection.mutable.HashMap
import com.twitter.util.TimerTask
import org.slf4j.{LoggerFactory, Logger}
import com.twitter.util.Timer
import com.twitter.finagle.stats.{Counter, Stat, StatsReceiverWithCumulativeGauges}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.conversions.time._

object Slf4jStatsReceiver {

  def apply(): Slf4jStatsReceiver = {
    val logger: Logger = LoggerFactory.getLogger(Slf4jStatsReceiver.getClass)
    new Slf4jStatsReceiver(logger)
  }
}

class Slf4jStatsReceiver(logger: Logger, timer: Timer) extends StatsReceiverWithCumulativeGauges {

  val repr = logger
  var timerTasks = new HashMap[Seq[String], TimerTask]

  // Timer here will never be released. This is ok since this class is used for debugging only.
  def this(logger: Logger) = this(logger, DefaultTimer.twitter)

  def stat(name: String*) = new Stat {
    def add(value: Float) {
      logger.debug("{} add {}", formatName(name), value)
    }
  }

  def counter(name: String*): Counter with Object {def incr(delta: Int): Unit} = new Counter {
    def incr(delta: Int) {
      logger.debug("{} increment {}", formatName(name), delta)
    }
  }

  protected[this] def registerGauge(name: Seq[String], f: => Float) = synchronized {
    deregisterGauge(name)
    timerTasks(name) = timer.schedule(10.seconds) {
      logger.debug("{}: {}", formatName(name), f)
    }
  }

  protected[this] def deregisterGauge(name: Seq[String]) {
    timerTasks.remove(name) foreach {
      _.cancel()
    }
  }

  private[this] def formatName(description: Seq[String]) = {
    description mkString "/"
  }
}

