package example

import java.util.concurrent._

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

  private val underlyingScheduler = AsyncScheduler(scheduler, ec, r, executionModel)


  protected def executeAsync(r: Runnable): Unit =
    ec.execute(r)

  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    if (initialDelay <= 0) {
      val startedAtMillis = System.currentTimeMillis()
      ec.execute(r)
      val finishedAtMillis = System.currentTimeMillis()
//      Cancelable.empty
      StatsAwareCancelable(finishedAtMillis - startedAtMillis)
    } else {
      StatsAwareCancelable(initialDelay, unit, r, this, scheduler)
    }
  }

//  override def scheduleOnce(initialDelay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
//    if (initialDelay <= 0) {
//      val startedAtMillis = System.currentTimeMillis()
//      ec.execute(r)
//      val finishedAtMillis = System.currentTimeMillis()
//      StatsAwareCancelable(finishedAtMillis - startedAtMillis)
//    } else {
//      val statsAwareScheduler = this
//
//      // val deferred = new ShiftedRunnable(r, this)
//      // equivalent to ShiftedRunnable
//      val deferred = new Runnable {
//        override def run() = {
//          r match {
//            case ref: TrampolinedRunnable =>
//              // Cannot run directly, otherwise we risk a trampolined
//              // execution on the current thread and call stack and that
//              // isn't what we want
//              statsAwareScheduler.execute(new StartAsyncBatchRunnable(ref, statsAwareScheduler))
//            case _ =>
//              statsAwareScheduler.execute(r)
//          }
//        }
//      }
//      val task: ScheduledFuture[_] = scheduler.schedule(deferred, initialDelay, unit)
//      // TODO: deferred the calculation of this time
//      StatsAwareCancelable(() => task.cancel(true), finishedAtMillis - startedAtMillis)
//    }
//  }

  override def scheduleWithFixedDelay(initialDelay: Long, delay: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    val sub = StatsAwareCancelable()

    def loop(initialDelay: Long, delay: Long): Unit = {
      if (!sub.isCanceled)
        sub := scheduleOnce(initialDelay, unit, () => {
          val etr = ElapsedTimeAwareRunnable(r)
          etr.run()
          loop(delay, delay)
        })
    }

    loop(initialDelay, delay)
    sub
  }

  override def scheduleAtFixedRate(initialDelay: Long, period: Long, unit: TimeUnit, r: Runnable): IStatsAwareCancelable = {
    val sub = StatsAwareCancelable()

    def loop(initialDelayMs: Long, periodMs: Long): Unit =
      if (!sub.isCanceled) {
        sub := scheduleOnce(initialDelayMs, TimeUnit.MILLISECONDS, () => {
          // Measuring the duration of the task
          //            val startedAtMillis = System.currentTimeMillis()
          //            r.run()
          val etr = ElapsedTimeAwareRunnable(r)
          etr.run()
          val delay = {
            //              val durationMillis = System.currentTimeMillis() - startedAtMillis
            //              val d = periodMs - durationMillis
            val diff = periodMs - etr.elapsedTimeInMilliseconds
            if (diff >= 0) diff else 0
          }

          // Recursive call
          loop(delay, periodMs)
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
    System.currentTimeMillis() - startedTimeInMillis
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

