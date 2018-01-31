package example


object TaskApp extends App {
  import java.util.{Timer, TimerTask}


  def schedule(runnable: () => Unit): TimerTask = {
    val ttask = new TimerTask {
      override def run(): Unit = runnable()
    }
    new Timer().schedule(ttask, 0)
    ttask
  }


  def repeat(initialDelay: Long, period: Long, runnable: () => Unit): TimerTask = {
    val ttask = new TimerTask {
      override def run(): Unit = runnable()
    }

    new Timer().scheduleAtFixedRate(ttask, initialDelay, period)
    ttask
  }


  val f = schedule(() => println("holaaaaa"))

//  val f = repeat(3000, 1000, () => println("holaaaaa"))
}


