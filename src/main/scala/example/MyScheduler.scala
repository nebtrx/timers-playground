package example

import java.util.concurrent.{Executor, ScheduledExecutorService, TimeUnit}

import monix.execution.atomic.{AtomicAny, PaddingStrategy}
import monix.execution.cancelables.{AssignableCancelable, OrderedCancelable}
import monix.execution.{Cancelable, ExecutionModel, Scheduler, UncaughtExceptionReporter}
import monix.execution.schedulers._

import scala.annotation.{implicitNotFound, tailrec}
import scala.concurrent.ExecutionContext



@implicitNotFound(
  "Cannot find an implicit Scheduler, either " + "import monix.execution.Scheduler.Implicits.global or use a custom one")
trait TScheduler {
  def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable

  def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable

  def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable

}


final class MyScheduler private(
                                     scheduler: ScheduledExecutorService,
                                     ec: ExecutionContext,
                                     r: UncaughtExceptionReporter,
                                     val executionModel: ExecutionModel)
  extends ReferenceScheduler with BatchingScheduler with TScheduler {

  protected def executeAsync(r: Runnable): Unit =
    ec.execute(r)

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    var startedAtMillis: Long = 0
    var finishedAtMillis: Long = 0
    
    if (initialDelay <= 0) {
      startedAtMillis = currentTimeMillis()
      ec.execute(r)
      finishedAtMillis = currentTimeMillis()
      StatsAwareCancelable(finishedAtMillis - startedAtMillis)
    } else {
      val self = this
      
      // val deferred = new ShiftedRunnable(r, this)
      // equivalent to ShiftedRunnable
      val deferred = new Runnable {
        override def run() = {
          startedAtMillis = currentTimeMillis()
          r match {
            case ref: TrampolinedRunnable =>
              // Cannot run directly, otherwise we risk a trampolined
              // execution on the current thread and call stack and that
              // isn't what we want
              self.execute(new StartAsyncBatchRunnable(ref, self))
            case _ =>
              self.execute(r)
          }
          finishedAtMillis = currentTimeMillis()
        }
      }
      val task = scheduler.schedule(deferred, initialDelay, unit)
      StatsAwareCancelable(() => task.cancel(true), finishedAtMillis - startedAtMillis)
    }
  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    val sub = StatsAwareCancelable()
    var startedAtMillis:Long = 0
    var finishedAtMillis: Long = 0

    def loop(initialDelay: Long, delay: Long): Unit = {
      if (!sub.isCanceled)
        sub := scheduleOnce(initialDelay, unit, () => {
          startedAtMillis = currentTimeMillis()
          r.run()
          finishedAtMillis = currentTimeMillis()
          loop(delay, delay)
        })
    }

    loop(initialDelay, delay)
    sub
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    val sub = OrderedCancelable()

    def loop(initialDelayMs: Long, periodMs: Long): Unit =
      if (!sub.isCanceled) {
        sub := scheduleOnce(initialDelayMs, TimeUnit.MILLISECONDS, new Runnable {
          def run(): Unit = {
            // Measuring the duration of the task
            val startedAtMillis = currentTimeMillis()
            r.run()

            val delay = {
              val durationMillis = currentTimeMillis() - startedAtMillis
              val d = periodMs - durationMillis
              if (d >= 0) d else 0
            }

            // Recursive call
            loop(delay, periodMs)
          }
        })
      }

    val initialMs = TimeUnit.MILLISECONDS.convert(initialDelay, unit)
    val periodMs = TimeUnit.MILLISECONDS.convert(period, unit)
    loop(initialMs, periodMs)
    sub
  }


  override def reportFailure(t: Throwable): Unit =
    r.reportFailure(t)

  override def withExecutionModel(em: ExecutionModel): MyScheduler =
    new MyScheduler(scheduler, ec, r, em)

  private def calcElapsedExecutionTime(startedTimeInMillis : Long) = {
    currentTimeMillis() - startedTimeInMillis
  }
}

object MyScheduler {

  /** Builder for [[MyScheduler]].
    *
    * @param schedulerService is the Java `ScheduledExecutorService` that will take
    *        care of scheduling tasks for execution with a delay.
    * @param ec is the execution context that will execute all runnables
    * @param reporter is the [[UncaughtExceptionReporter]] that logs uncaught exceptions.
    * @param executionModel is the preferred
    *        [[monix.execution.ExecutionModel ExecutionModel]],
    *        a guideline for run-loops and producers of data.
    */
  def apply(
             schedulerService: ScheduledExecutorService,
             ec: ExecutionContext,
             reporter: UncaughtExceptionReporter,
             executionModel: ExecutionModel): MyScheduler =
    new MyScheduler(schedulerService, ec, reporter, executionModel)
}

