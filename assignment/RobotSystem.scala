trait Robot {
  def start(): Unit
  def shutdown(): Unit
  def status(): Unit = println("Robot is operational")
}

trait SpeechModule {
  def speak(message: String): Unit = println(s"Robot says: $message")
}

trait MovementModule {
  def moveForward(): Unit = println("Moving forward")
  def moveBackward(): Unit = println("Moving backward")
}

trait EnergySaver extends Robot {
  def activateEnergySaver(): Unit = println("Energy saver mode activated")
  abstract override def shutdown(): Unit = {
    println("Robot shutting down to save energy")
    super.shutdown()
  }
}

class BasicRobot extends Robot {
  override def start(): Unit = println("BasicRobot starting up")
  override def shutdown(): Unit = println("BasicRobot shutting down")
}

object RobotSystem extends App {
  val robot1 = new BasicRobot with SpeechModule with MovementModule
  robot1.start()
  robot1.status()
  robot1.speak("Hello!")
  robot1.moveForward()
  robot1.shutdown()

  println("-----")

  val robot2 = new BasicRobot with EnergySaver with MovementModule
  robot2.start()
  robot2.moveBackward()
  robot2.activateEnergySaver()
  robot2.shutdown()

  println("-----")

  val robot3 = new BasicRobot with MovementModule with EnergySaver
  robot3.start()
  robot3.moveForward()
  robot3.shutdown()
}
