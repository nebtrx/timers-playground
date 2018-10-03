package example.hyt

import example.hyt.consumers.executionStatus
import monix.eval.{MVar, Task}
import monix.execution.{Cancelable, CancelableFuture}

import scala.io.StdIn

object MonixMain extends App {

  import monix.execution.Scheduler.Implicits.global
  import monix.reactive._

  import concurrent.duration._

  case class State(cyclesCompleted: Long, cyclesLeft: Option[Long])

  case class HyperTimer(interval: FiniteDuration,
                        private val handler: State => Task[Unit],
                        private var stopEvaluator : State => Boolean,
                        private val stateMVar: MVar[State]) {

    // TODO: evaluate to return cancelable wrapped in an F or Task
    def start(): Cancelable = {
      Observable
        .interval(interval)
        // TODO: try out several consumers, one for executin and another for knowing when to stop
        .mapEval[Task, StoppingCondition](_ => tickHandler)
        .consumeWith(executionStatus)
        .runAsync
    }

    def stopIn(iterations: Long): Task[Unit] = { // TODO: Do the same as durations
      val c = for {
         s <- stateMVar.take
         newS = s.copy(cyclesLeft = Some(iterations))
         _ <- stateMVar.put(newS)
      } yield ()

      stopEvaluator = StoppingStrategies.stopIfNoCyclesLeft
      c
    }

    def repeat(iterations: Long): Task[Unit] = {
      for {
        s <- stateMVar.take
        newS = s.copy(cyclesLeft = s.cyclesLeft.map(_ + iterations))
        _ <- stateMVar.put(newS)
      } yield ()
    }

    // TODO Check several handlers paralelization
    private def tickHandler:Task[StoppingCondition] = {
      for {
//        _ <- Task {println("STARTING")}
        s <- stateMVar.take
        newS = s.copy(cyclesCompleted = s.cyclesCompleted + 1, cyclesLeft = s.cyclesLeft.map(_ - 1))
        _ <- handler(newS)
//        _ <- Task {println(s"S EVEL $stopEvaluator")}
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
    import StoppingStrategies._
    def apply(interval: FiniteDuration, handler: State => Task[Unit]): Task[HyperTimer] = {
      for {
        s  <- MVar(State(0, None))
        h = new HyperTimer(interval, handler, neverStop, s)
      } yield h
    }

    def apply(interval: FiniteDuration, iterations: Long, handler: State => Task[Unit]): Task[HyperTimer] = {
      for {
        s  <- MVar(State(0, Some(iterations)))
        h = new HyperTimer(interval, handler, stopIfNoCyclesLeft, s)
      } yield h
    }

    def start(interval: FiniteDuration, handler: State => Task[Unit]): Task[Cancelable] = {
      for {
        s  <- MVar(State(0, None))
        h = HyperTimer(interval, handler, neverStop, s)
        c = h.start()
      } yield c
    }

    def start(interval: FiniteDuration, iterations: Long, handler: State => Task[Unit]): Task[Cancelable] = {
      for {
        s  <- MVar(State(0, Option(iterations)))
        h = HyperTimer(interval, handler, stopIfNoCyclesLeft, s)
        c = h.start()
      } yield c
    }
  }

  // TODO: Move

  private object StoppingStrategies  {
    val stopIfNoCyclesLeft: State => Boolean = (state: State) =>  state.cyclesLeft.contains(0)
    val neverStop: State => Boolean = (state : State) => false
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

  val computation: Task[Unit] = for {
    ht <-  HyperTimer(1000.milliseconds, fn)
    f = ht.start()
    _ <- Task { StdIn.readLine()}
    _ = println("STPOOINGGGGG --- IN 5")
    _ <- ht.stopIn(5)
//    _ <- Task { StdIn.readLine()}
    _ = println("EXTENDING 10 more times")
    _ <- ht.repeat(10)
    _ <- Task { StdIn.readLine()}
    _ = println("CANCELLLING")
    _ = f.cancel()
  } yield ():Unit

  computation.runAsync

  StdIn.readLine()
  println("FINISHINGGG")

}
