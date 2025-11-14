trait Drone {
  def activate(): Unit
  def deactivate(): Unit
  def status(): Unit = println("Drone status: Operational")
}

trait NavigationModule extends Drone {
  def flyTo(destination: String): Unit = println(s"Flying to $destination")
  abstract override def deactivate(): Unit = {
    println("Navigation systems shutting down")
    super.deactivate()
  }
}

trait DefenseModule extends Drone {
  def activateShields(): Unit = println("Shields activated")
  abstract override def deactivate(): Unit = {
    println("Defense systems deactivated")
    super.deactivate()
  }
}

trait CommunicationModule extends Drone {
  def sendMessage(msg: String): Unit = println(s"Sending message: $msg")
  abstract override def deactivate(): Unit = {
    println("Communication module shutting down")
    super.deactivate()
  }
}

class BasicDrone extends Drone {
  def activate(): Unit = println("Basic Drone Activated")
  def deactivate(): Unit = println("Basic Drone Deactivated")
}

object DroneSystem extends App {

  val drone1 = new BasicDrone with NavigationModule with DefenseModule
  drone1.activate()
  drone1.flyTo("Mars")
  drone1.activateShields()
  drone1.deactivate()
  println()

  val drone2 = new BasicDrone with CommunicationModule with NavigationModule
  drone2.activate()
  drone2.sendMessage("Hello Galaxy")
  drone2.flyTo("Europa")
  drone2.deactivate()
  println()

  val drone3 = new BasicDrone with NavigationModule with DefenseModule with CommunicationModule
  drone3.activate()
  drone3.flyTo("Titan")
  drone3.activateShields()
  drone3.sendMessage("Mission Complete")
  drone3.deactivate()
  println()

  val drone4 = new BasicDrone with CommunicationModule with DefenseModule with NavigationModule
  drone4.activate()
  drone4.sendMessage("Testing")
  drone4.activateShields()
  drone4.flyTo("Ganymede")
  drone4.deactivate()
}
