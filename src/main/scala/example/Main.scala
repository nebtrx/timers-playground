package example

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{TimeUnit, _}

import monix.execution.{Cancelable, ExecutionModel, Scheduler, UncaughtExceptionReporter}

import scala.concurrent.duration._
import monix.execution.Scheduler.{global => scheduler}
import monix.execution.UncaughtExceptionReporter.LogExceptionsToStandardErr
import monix.execution.schedulers._
import monix.reactive.Observable

import scala.concurrent.ExecutionContext

object Main extends App {

//  scheduler.execute(new Runnable {
//    def run(): Unit = {
//      println("Hello, world!")
//    }
//  })

//  val cancelable = scheduler.scheduleOnce(
//    5, TimeUnit.SECONDS,
//    new Runnable {
//      def run(): Unit = {
//        println("Hello, world!")
//      }
//    })

//  val s = TestScheduler()

  val ec = ExecutionContext.Implicits.global
  val daemonic = true
  val reporter = LogExceptionsToStandardErr
  val name = "my scheduler"

  val threadFactory = new ThreadFactory {
    def newThread(r: Runnable) = {
      val thread = new Thread(r)
      thread.setName(name + "-" + thread.getId)
      thread.setDaemon(daemonic)
      thread.setUncaughtExceptionHandler(
        new UncaughtExceptionHandler {
          override def uncaughtException(t: Thread, e: Throwable): Unit =
            reporter.reportFailure(e)
        })

      thread
    }
  }

  lazy val scheduledExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(threadFactory)

  lazy val DefaultScheduledExecutor: ScheduledExecutorService = scheduledExecutor

  val s: TScheduler = MyScheduler(DefaultScheduledExecutor,
                      ec,
                      UncaughtExceptionReporter(ec.reportFailure),
                      ExecutionModel.SynchronousExecution)


//  val c = s.scheduleOnce(2.seconds) {
//    println("Hello, world!")
//  }

//  write my own scheduler based on ReferenceSchduler con un Cancelable basado on top of OrderedCancelable

  val c: IStatsAwareCancelable = s.scheduleOnce(
    3, TimeUnit.SECONDS,
    () => {
      println("Startingggg")
      Thread.sleep(10000)
      println("Fixed delay task")
      this.notify()
    })

//  Thread.sleep(6000)

//  s.scheduleWithFixedDelay(1.seconds, 2.seconds) {
//    println("Fixed delay task 2")
//  }

  readLine()
  println(s"########## ${c.totalElapsedTime}")



}
