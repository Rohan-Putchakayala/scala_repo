package modules

import akka.actor.typed.ActorSystem
import services.ReservationService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class ReservationScheduler @Inject()(
  reservationService: ReservationService,
  system: ActorSystem[Nothing]
)(implicit ec: ExecutionContext) {

  private val reminders = system.scheduler.scheduleAtFixedRate(1.minute, 1.minute)(new Runnable {
    override def run(): Unit = {
      reservationService.processReminders()
    }
  })

  private val releases = system.scheduler.scheduleAtFixedRate(1.minute, 1.minute)(new Runnable {
    override def run(): Unit = {
      reservationService.processAutoReleases()
    }
  })
}