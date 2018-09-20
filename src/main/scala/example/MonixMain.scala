package example

import monix.eval.{Callback, MVar, Task}
import monix.execution.Ack.{Continue, Stop}
import monix.execution.cancelables.AssignableCancelable
import monix.execution.{Ack, Cancelable, CancelableFuture, Scheduler}
import monix.reactive.observers.Subscriber

import scala.io.StdIn

object MonixMain extends App {

  import monix.execution.Scheduler.Implicits.global
  import monix.reactive._

  import concurrent.duration._

  case class State(cyclesCompleted: Long, cyclesLeft: Option[Long])

  case class HyperTimer(interval: FiniteDuration,
                        handler: State => Task[Unit],
                        stopEvaluator : State => Boolean,
                        stateMVar: MVar[State]) {
    def start(): Cancelable = {
      Observable
        .interval(interval)
        // probar con consumer, de hecho con varios, uno apra ejecutar y uno para saber si se detiene
        .mapEval[Task, StoppingCondition](_ => process)
        .consumeWith(excutionStatusConsumer)
        .runAsync
    }

    // paralelizar con load balancer varios eventos o algo asi

    sealed trait StoppingCondition
    case object NotMatched extends StoppingCondition
    case object Matched extends StoppingCondition

    val excutionStatusConsumer: Consumer[StoppingCondition, Unit] =
      new Consumer[StoppingCondition, Unit] {
        def createSubscriber(cb: Callback[Unit], s: Scheduler): (Subscriber.Sync[StoppingCondition], AssignableCancelable) = {
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
              cb.onSuccess(():Unit)
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


    private def process:Task[StoppingCondition] = {
      for {
//        _ <- Task {println("STARTING")}
        s <- stateMVar.take
//        _ <- Task {println("READ")}
        newS = s.copy(cyclesCompleted = s.cyclesCompleted + 1, cyclesLeft = s.cyclesLeft.map(_ - 1))
        _ <- handler(newS)
        shouldStop = stopEvaluator(newS)
        es <- if (shouldStop) stop(s, stateMVar)
             else continue(newS, stateMVar)
      } yield es
    }

    private def continue(state: State, mvar: MVar[State]): Task[NotMatched.type] =
      for {
        _ <- mvar.put(state)
      } yield NotMatched

    private def stop(state: State, mvar: MVar[State]): Task[Matched.type] =
      for {
        _ <- mvar.put(state)
      } yield Matched

  }

  object HyperTimer {
    def apply(interval: FiniteDuration, handler: State => Task[Unit]): Task[HyperTimer] = {
      for {
        s  <- MVar(State(0, Option(10)))
        h = new HyperTimer(interval, handler, stopIfNoCyclesLeft, s)
      } yield h
    }

    def start(interval: FiniteDuration, handler: State => Task[Unit]): Task[Cancelable] = {
      for {
        s  <- MVar(State(0, Option(10)))
        h = new HyperTimer(interval, handler, stopIfNoCyclesLeft, s)
        c = h.start()
      } yield c
    }

    private def stopIfNoCyclesLeft(state: State) = state.cyclesLeft.contains(0)

  }

  //  // We first build an observable that emits a tick per second,
  //  // the series of elements being an auto-incremented long
  //  val source = Observable.interval(1.second)
  //    // Filtering out odd numbers, making it emit every 2 seconds
  //    .filter(_ % 2 == 0)
  //    // We then make it emit the same element twice
  //    .flatMap(x => Observable(x, x))
  //    // This stream would be infinite, so we limit it to 10 items
  //    .take(10)
  //
  //  // Observables are lazy, nothing happens until you subscribe...
  //  val cancelable = source
  //    // On consuming it, we want to dump the contents to stdout
  //    // for debugging purposes
  //    .dump("O")
  //    // Finally, start consuming it
  //    .subscribe()

  val fn = (s: State) => Task{println(s"DEBUGGER $s")}

  val c: Either[CancelableFuture[Cancelable], Cancelable] =
    HyperTimer.start(1.second, fn).coeval.value


  StdIn.readLine()
  c.map(_.cancel())
}
