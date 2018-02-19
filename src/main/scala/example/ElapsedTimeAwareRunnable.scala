package example


import java.util.concurrent.CompletableFuture

import monix.execution.Cancelable

class ElapsedTimeAwareRunnable(runnable: Runnable, callback: (Long) => Unit) extends Runnable {
  private var _elapsedTimeInMilliseconds: Long = 0

  override def run(): Unit = {
    val startedAtMillis = currentTimeMillis
    println("Meassuringggggg")
    runnable.run()
    this.wait()
    val finishedAtMillis = currentTimeMillis
    println(s"Finish Meassuringggggg $runnable")
    _elapsedTimeInMilliseconds = finishedAtMillis - startedAtMillis
    println(s"###### Started: $startedAtMillis  FInished: $finishedAtMillis")
    if (callback != null) callback(elapsedTimeInMilliseconds)
  }

  def elapsedTimeInMilliseconds : Long = {
    _elapsedTimeInMilliseconds
  }

  private def currentTimeMillis = {
    System.currentTimeMillis()
  }
}


object ElapsedTimeAwareRunnable {
  def apply(runnable: Runnable, callback: (Long) => Unit): ElapsedTimeAwareRunnable =
    new ElapsedTimeAwareRunnable(runnable, callback)

  def apply(runnable: Runnable): ElapsedTimeAwareRunnable =
    new ElapsedTimeAwareRunnable(runnable, null)
}
