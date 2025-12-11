package urban_mobility_proj

import java.io._
import scala.util.Random
import java.time._

object DataGenerator extends App {

  val file = new PrintWriter(new File("urbanmove_trips.csv"))
  val areas = Array("MG Road", "Indira Nagar", "Koramangala", "Whitefield", "Marathahalli",
    "HSR Layout", "BTM", "Jayanagar")

  val vehicleTypes = Array("AUTO", "TAXI", "BIKE")
  val paymentMethods = Array("CASH", "UPI", "CARD")

  val rand = new Random()

  def randomDateTime(): LocalDateTime = {
    val now = LocalDateTime.now()
    now.minusMinutes(rand.nextInt(100000))
  }

  file.write("tripId,driverId,vehicleType,startTime,endTime,startLocation,endLocation,distanceKm,fareAmount,paymentMethod,customerRating\n")

  for (i <- 1 to 1000000) {
    val start = randomDateTime()
    val duration = rand.nextInt(50) + 5

    val end = start.plusMinutes(duration)
    val distance = (rand.nextDouble() * 15 + 1).formatted("%.2f").toDouble
    val fare = (distance * (rand.nextInt(10) + 10)).formatted("%.2f").toDouble

    file.write(
      s"$i,${rand.nextInt(5000)},${vehicleTypes(rand.nextInt(3))},$start,$end," +
        s"${areas(rand.nextInt(areas.length))},${areas(rand.nextInt(areas.length))}," +
        s"$distance,$fare,${paymentMethods(rand.nextInt(3))}," +
        f"${rand.nextDouble() * 4 + 1}%.2f\n"
    )
  }
  file.close()
}
