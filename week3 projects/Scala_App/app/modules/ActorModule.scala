package modules

import actors.NotificationActor
import akka.actor.{ActorRef, ActorSystem, Props}
import com.google.inject.{AbstractModule, Provides}
import javax.inject.{Named, Singleton}
import services.{EmailService, KafkaProducerService}

class ActorModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[EmailService]).asEagerSingleton()
    bind(classOf[KafkaProducerService]).asEagerSingleton()
  }

  @Provides @Singleton @Named("notification-actor")
  def provideNotificationActor(
                                emailService: EmailService,
                                kafkaProducer: KafkaProducerService,
                                system: ActorSystem
                              ): ActorRef = {
    system.actorOf(
      NotificationActor.props(emailService, kafkaProducer),
      "notification-actor"
    )
  }
}