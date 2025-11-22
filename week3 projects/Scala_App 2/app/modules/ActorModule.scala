package modules

import actors.ReservationActor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Provides}
import services.{EmailService, KafkaProducerService}
import modules.ReservationScheduler

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class ActorModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ReservationScheduler]).asEagerSingleton()
  }

  @Provides
  @Singleton
  def provideActorSystem(): ActorSystem[Nothing] = {
    ActorSystem(
      Behaviors.empty,
      "room-management-system"
    )
  }

  @Provides
  @Singleton
  def provideReservationActor(
    emailService: EmailService,
    kafkaProducer: KafkaProducerService,
    system: ActorSystem[Nothing]
  )(implicit ec: ExecutionContext): ActorRef[ReservationActor.Command] = {
    system.systemActorOf(
      ReservationActor(emailService, kafkaProducer),
      "reservation-actor"
    )
  }
}
