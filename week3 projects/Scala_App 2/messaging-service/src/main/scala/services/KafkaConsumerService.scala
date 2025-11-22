package services

import actors.{AdminActor, RoomServiceActor}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Sink
import com.typesafe.config.Config
import models.{AdminNotification, RoomPreparationNotification, RoomReleaseNotification}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext
import scala.util.Try

class KafkaConsumerService(
                            config: Config,
                            roomServiceActor: ActorRef[RoomServiceActor.Command],
                            adminActor: ActorRef[AdminActor.Command]
                          )(implicit system: ActorSystem[_], ec: ExecutionContext) {

  private val bootstrapServers = config.getString("kafka.bootstrap-servers")

  private val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId("room-management-messaging-service")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def startRoomPreparationConsumer(): Unit = {
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics("room-preparation-notifications"))
      .runWith(Sink.foreach { record =>
        Try {
          implicit val format = Json.format[RoomPreparationNotification]
          val notification = Json.parse(record.value()).as[RoomPreparationNotification]
          roomServiceActor ! RoomServiceActor.ProcessRoomPreparation(notification)
        }.recover {
          case ex: Exception =>
            system.log.error(s"Failed to process room preparation notification: ${ex.getMessage}")
        }
      })

    system.log.info("Room Preparation Notification Consumer started")
  }

  def startRoomReleaseConsumer(): Unit = {
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics("room-release-notifications"))
      .runWith(Sink.foreach { record =>
        Try {
          implicit val format = Json.format[RoomReleaseNotification]
          val notification = Json.parse(record.value()).as[RoomReleaseNotification]
          roomServiceActor ! RoomServiceActor.ProcessRoomRelease(notification)
        }.recover {
          case ex: Exception =>
            system.log.error(s"Failed to process room release notification: ${ex.getMessage}")
        }
      })

    system.log.info("Room Release Notification Consumer started")
  }

  def startAdminNotificationConsumer(): Unit = {
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics("admin-notifications"))
      .runWith(Sink.foreach { record =>
        Try {
          implicit val format = Json.format[AdminNotification]
          val notification = Json.parse(record.value()).as[AdminNotification]
          adminActor ! AdminActor.ProcessAdminNotification(notification)
        }.recover {
          case ex: Exception =>
            system.log.error(s"Failed to process admin notification: ${ex.getMessage}")
        }
      })

    system.log.info("Admin Notification Consumer started")
  }

  def startAllConsumers(): Unit = {
    startRoomPreparationConsumer()
    startRoomReleaseConsumer()
    startAdminNotificationConsumer()
  }
}
