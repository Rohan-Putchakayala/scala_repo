abstract class Spacecraft(val fuelLevel: Int) {
  def launch(): Unit
  def land(): Unit = println("Default landing sequence initiated")
}

trait Autopilot {
  def autoNavigate(): Unit = println("Autopilot navigation engaged")
}

class CargoShip(fuelLevel: Int) extends Spacecraft(fuelLevel) with Autopilot {
  def launch(): Unit = println("CargoShip launch initiated with cargo protocols")
  override def land(): Unit = println("CargoShip specialized landing sequence")
}

class PassengerShip(fuelLevel: Int) extends Spacecraft(fuelLevel) with Autopilot {
  def launch(): Unit = println("PassengerShip launch initiated for passenger mission")
  final override def land(): Unit = println("PassengerShip final landing sequence")
}

final class LuxuryCruiser(fuelLevel: Int) extends PassengerShip(fuelLevel) {
  def playEntertainment(): Unit = println("LuxuryCruiser entertainment system activated")
}

object Main extends App {
  val cargoShip = new CargoShip(80)
  val passengerShip = new PassengerShip(90)
  val luxuryCruiser = new LuxuryCruiser(100)

  cargoShip.launch()
  cargoShip.land()
  cargoShip.autoNavigate()

  passengerShip.launch()
  passengerShip.land()
  passengerShip.autoNavigate()

  luxuryCruiser.launch()
  luxuryCruiser.land()
  luxuryCruiser.autoNavigate()
  luxuryCruiser.playEntertainment()
}
