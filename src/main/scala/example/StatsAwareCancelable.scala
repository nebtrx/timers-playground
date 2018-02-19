package example

import java.util.concurrent.{Callable, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import monix.execution.{Cancelable, Scheduler}
import monix.execution.cancelables.{AssignableCancelable, OrderedCancelable}
import monix.execution.schedulers.ShiftedRunnable

import scala.concurrent.Await


private case class Stats(var totalElapsedTime: Long, var totalCycleCount: Long)

private object Stats {
  def default = Stats(0, 0)
}

// TODO: rename this
trait IStatsAwareCancelable extends Cancelable {
  def totalElapsedTime:Long

  def totalCycleCount: Long
}

final class StatsAwareCancelable private(action: Cancelable)
  extends AssignableCancelable.Multi with IStatsAwareCancelable {

  // cicles left
  private var _totalElapsedTime: Long = 0

  // May swithc later to something called _totalScheduledCycleCount
  private var _totalCycleCount: Long = 0

  private var _orderedCancelable = OrderedCancelable(action)

  override def isCanceled: Boolean = _orderedCancelable.isCanceled

  override def cancel(): Unit = _orderedCancelable.cancel()

  def `:=`(value: Cancelable): this.type = {
    _orderedCancelable := value
    _totalCycleCount += 1
    this
  }

  def totalElapsedTime: Long = _totalElapsedTime

  def totalCycleCount: Long = _totalCycleCount
}

object StatsAwareCancelable {
  /** Builder for [[StatsAwareCancelable]]. */
  def apply(): StatsAwareCancelable =
    new StatsAwareCancelable(null)

  /** Builder for [[StatsAwareCancelable]]. */
  def apply(initial: Cancelable): StatsAwareCancelable =
    new StatsAwareCancelable(initial)

  /** Builder for [[StatsAwareCancelable]]. */
  def apply(totalElapsedTime: Long): StatsAwareCancelable = {
    var cancelable = new StatsAwareCancelable(null)
    cancelable._totalElapsedTime = totalElapsedTime
    cancelable
  }

  /** Builder for [[StatsAwareCancelable]]. */
  def apply(callback: () => Unit, totalElapsedTime: Long): StatsAwareCancelable = {
    var cancelable = StatsAwareCancelable(Cancelable(callback))
    cancelable._totalElapsedTime = totalElapsedTime
    cancelable
  }

  /** Builder for [[StatsAwareCancelable]]. */
  def apply(initialDelay: Long, unit: TimeUnit, r: Runnable, s:Scheduler, ss: ScheduledExecutorService): StatsAwareCancelable = {
    var cancelable = StatsAwareCancelable()
    val deferred = ElapsedTimeAwareRunnable(new ShiftedRunnable(r, s), (et) => cancelable._totalElapsedTime = et)
    val task = ss.schedule(deferred, initialDelay, unit)
    cancelable._orderedCancelable = OrderedCancelable(Cancelable(() => task.cancel(true)))
    cancelable
  }
}

