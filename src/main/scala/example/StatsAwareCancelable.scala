package example

import monix.execution.Cancelable
import monix.execution.cancelables.{AssignableCancelable, OrderedCancelable}


private case class Stats(var totalElapsedTime: Long, var totalCycleCount: Long)

private object Stats {
  def default = Stats(0, 0)
}

// TODO: rename this
trait IStatsAwareCancelable extends Cancelable {
  def totalElapsedTime:Long

  def totalCycleCount: Long
}

final class StatsAwareCancelable private(initial: Cancelable)
  extends AssignableCancelable.Multi with IStatsAwareCancelable {
  // cicles left

  lazy val currentKey: Int = hashCode()

  private var orderedCancelable = OrderedCancelable(initial)

  private var stats = Map[Int, Stats](currentKey -> Stats.default)

  override def isCanceled: Boolean = orderedCancelable.isCanceled

  override def cancel(): Unit = orderedCancelable.cancel()

  def `:=`(value: Cancelable): this.type = {
    val underlying = value.asInstanceOf[StatsAwareCancelable].orderedCancelable
    // TODO: review this, I dont think is going to work as expected.
    //       I must fully understand how := works
    orderedCancelable := underlying
    this
  }

  def totalElapsedTime: Long = currentStats.totalElapsedTime

  def totalCycleCount: Long = currentStats.totalCycleCount

  private def totalElapsedTime_=(value: Long): Unit = {
    currentStats.totalElapsedTime = value
  }

  private def totalCycleCount_=(value: Long): Unit = {
    currentStats.totalCycleCount = value
  }

  protected def currentStats: Stats = stats.getOrElse(currentKey, Stats.default)
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
}

