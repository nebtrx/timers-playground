package example.hyt

import monix.eval.Callback
import monix.execution.Ack.{Continue, Stop}
import monix.execution.{Ack, Scheduler}
import monix.execution.cancelables.AssignableCancelable
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber

package object consumers {

  // TODO: Package visibility only
  val executionStatus: Consumer[StoppingCondition, Unit] =
    (cb: Callback[Unit], s: Scheduler) => {
      val out: Subscriber.Sync[StoppingCondition] = new Subscriber.Sync[StoppingCondition] {
        implicit val scheduler = s

        def onNext(elem: StoppingCondition): Ack = {
          elem match {
            case NotMatched => Continue
            case Matched => Stop
          }
        }

        def onComplete(): Unit = {
          // We are done so we can signal the final result
          cb.onSuccess((): Unit)
        }

        def onError(ex: Throwable): Unit = {
          // Error happened, so we signal the error
          cb.onError(ex)
        }
      }

      // Returning a tuple of our subscriber and a dummy
      // AssignableCancelable because we don't intend to use it
      (out, AssignableCancelable.single())
    }

}
