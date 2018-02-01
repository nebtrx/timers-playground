package example

import java.util.concurrent.TimeUnit

import monix.execution.{ExecutionModel, Scheduler}

import scala.concurrent.duration._
import monix.execution.Scheduler.{global => scheduler}
import monix.execution.schedulers.TestScheduler

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

  val s = Scheduler(ExecutionModel.SynchronousExecution)


//  val c = s.scheduleOnce(2.seconds) {
//    println("Hello, world!")
//  }

  val c = scheduler.scheduleAtFixedRate(
    3, 5, TimeUnit.SECONDS,
    new Runnable {
      def run(): Unit = {
        println("Fixed delay task")
      }
    })

  scheduler.scheduleWithFixedDelay(1.seconds, 2.seconds) {
    println("Fixed delay task 2")
  }

  readLine()


}
