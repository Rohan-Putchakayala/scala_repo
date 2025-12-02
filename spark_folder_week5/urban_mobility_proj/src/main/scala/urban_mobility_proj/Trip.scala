package urban_mobility_proj

case class Trip(
                 tripId: Long,
                 driverId: Int,
                 vehicleType: String,
                 startTime: String,
                 endTime: String,
                 startLocation: String,
                 endLocation: String,
                 distanceKm: Double,
                 fareAmount: Double,
                 paymentMethod: String,
                 customerRating: Double
               )
