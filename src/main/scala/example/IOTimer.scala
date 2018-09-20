package example
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.{Executors, ScheduledExecutorService, ThreadFactory}

import cats.effect.{IO, Timer}
import example.Main.{daemonic, name, reporter, scheduledExecutor}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, TimeUnit, _}
import example.MyTimer
import monix.tail.Iterant

object IOTimer extends App {
  lazy val DefaultScheduledExecutor: ScheduledExecutorService = scheduledExecutor
  val ec = ExecutionContext.Implicits.global
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

  val timer = new  MyTimer(ec,scheduledExecutor )



  val ioa = IO { println("hey!") }

//  val gg = Iterant[IO].intervalAtFixedRate(3.seconds)


  val program: IO[Unit] =
    for {
      startTime <- timer.clockMonotonic(MILLISECONDS)
      _ <- ioa
      _ <- ioa
      endTime <- timer.clockMonotonic(MILLISECONDS)

      ts <- timer.clockRealTime(MILLISECONDS)
      _ <- IO { println(s"real time! $ts") }
      _ <- IO { println(s"ELAPSED TIME ${endTime - startTime}") }
    } yield ()

  program.unsafeRunSync()


}

final class MyTimer(ec: ExecutionContext, sc: ScheduledExecutorService) extends Timer[IO] {

  override def clockRealTime(unit: TimeUnit): IO[Long] =
    IO(unit.convert(System.currentTimeMillis(), MILLISECONDS))

  override def clockMonotonic(unit: TimeUnit): IO[Long] =
    IO(unit.convert(System.nanoTime(), NANOSECONDS))

  override def sleep(timespan: FiniteDuration): IO[Unit] =
    IO.cancelable { cb =>
      val tick = new Runnable {
        def run() = ec.execute(new Runnable {
          def run() = cb(Right(()))
        })
      }
      val f = sc.schedule(tick, timespan.length, timespan.unit)
      IO(f.cancel(false))
    }

  override def shift: IO[Unit] =
    IO.async(cb => ec.execute(new Runnable {
      def run() = cb(Right(()))
    }))

}

