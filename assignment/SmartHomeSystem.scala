trait Device {
  def turnOn(): Unit
  def turnOff(): Unit
}

trait EnergySaver {
  def saveEnergy(): Unit
}

trait VoiceControl {
  def turnOn(): Unit
  def turnOff(): Unit
}

class SmartLight extends Device with EnergySaver {
  def turnOn(): Unit = println("Light turned ON")
  def turnOff(): Unit = println("Light turned OFF")
  def saveEnergy(): Unit = println("Light dimmed to save energy")
}

class SmartFan extends Device {
  def turnOn(): Unit = println("Fan turned ON")
  def turnOff(): Unit = println("Fan turned OFF")
}

object SmartHomeSystem {
  def main(args: Array[String]): Unit = {

    val light = new SmartLight
    val fan = new SmartFan

    val voiceLight = new SmartLight with VoiceControl {
      override def turnOn(): Unit = super[SmartLight].turnOn()
      override def turnOff(): Unit = super[SmartLight].turnOff()
    }

    light.turnOn()
    light.saveEnergy()
    light.turnOff()

    fan.turnOn()
    fan.turnOff()

    voiceLight.turnOn()
    voiceLight.turnOff()
  }
}
