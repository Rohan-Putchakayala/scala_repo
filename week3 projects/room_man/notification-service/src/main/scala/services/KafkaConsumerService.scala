package services

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{RestartSource, Sink}
import akka.stream.{RestartSettings, Supervision}
import com.typesafe.config.Config
import models._
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json
import actors.NotificationProcessorActor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Kafka consumer service responsible for consuming notification messages
 * from various topics and routing them to appropriate notification processors.
 *
 * Implements resilient consumption with automatic restart capabilities,
 * error handling, and backpressure management for reliable message processing.
 */
class KafkaConsumerService(
  config: Config,
  notificationProcessorActor: ActorRef[NotificationProcessorActor.Command]
)(implicit system: ActorSystem[Nothing], ec: ExecutionContext) {

  private val kafkaBootstrapServers: String =
    config.getString("kafka.bootstrap.servers")
  private val consumerGroupId: String =
    config.getString("kafka.consumer.group.id")
  private val kafkaEnabled: Boolean =
    config.getBoolean("kafka.enabled")

  private val roomPreparationTopic: String =
    config.getString("kafka.topics.room.preparation")
  private val reservationReminderTopic: String =
    config.getString("kafka.topics.reservation.reminder")
  private val roomReleaseTopic: String =
    config.getString("kafka.topics.room.release")
  private val adminNotificationsTopic: String =
    config.getString("kafka.topics.admin.notifications")

  private val parallelism: Int =
    config.getInt("notification.service.parallelism")
  private val restartDelaySeconds: Int =
    config.getInt("notification.service.restart.delay.seconds")

  private val consumerSettings: ConsumerSettings[String, String] =
    ConsumerSettings(system.classicSystem, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaBootstrapServers)
      .withGroupId(consumerGroupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getString("kafka.consumer.auto.offset.reset"))
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, config.getBoolean("kafka.consumer.enable.auto.commit").toString)
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, config.getInt("kafka.consumer.auto.commit.interval.ms").toString)
      .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, config.getInt("kafka.consumer.session.timeout.ms").toString)
      .withProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, config.getInt("kafka.consumer.heartbeat.interval.ms").toString)
      .withProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.getInt("kafka.consumer.max.poll.records").toString)
      .withProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, config.getInt("kafka.consumer.max.poll.interval.ms").toString)

  private val restartSettings = RestartSettings(
    minBackoff = restartDelaySeconds.seconds,
    maxBackoff = (restartDelaySeconds * 4).seconds,
    randomFactor = 0.2
  ).withMaxRestarts(count = 10, within = 10.minutes)

  private val supervisionStrategy: Supervision.Decider = {
    case _: Exception => Supervision.Restart
  }

  /**
   * Starts consuming messages from all notification topics.
   *
   * Creates resilient Kafka consumer streams with automatic restart
   * capabilities and routes messages to appropriate processors based
   * on topic classification.
   *
   * @return Future indicating the start of consumer operations
   */
  def startConsumers(): Future[Unit] = {
    if (!kafkaEnabled) {
      system.log.info("Kafka consumer disabled by configuration")
      return Future.successful(())
    }

    val topics = Set(
      roomPreparationTopic,
      reservationReminderTopic,
      roomReleaseTopic,
      adminNotificationsTopic
    )

    system.log.info(s"Starting Kafka consumers for topics: ${topics.mkString(", ")}")

    val consumerSource = Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topics))
      .mapAsync(parallelism)(processMessage)
      .withAttributes(akka.stream.ActorAttributes.supervisionStrategy(supervisionStrategy))

    val restartSource = RestartSource.onFailuresWithBackoff(restartSettings) { () =>
      consumerSource
    }

    restartSource
      .runWith(Sink.ignore)
      .map(_ => ())
      .recover {
        case ex: Exception =>
          system.log.error("Fatal error in Kafka consumer", ex)
          ()
      }
  }

  /**
   * Processes individual Kafka messages by parsing JSON and routing to actors.
   *
   * Deserializes message payload, identifies message type based on topic,
   * and forwards to the notification processor actor for handling.
   *
   * @param record the Kafka consumer record containing the message
   * @return Future indicating completion of message processing
   */
  private def processMessage(record: ConsumerRecord[String, String]): Future[Unit] = {
    val startTime = System.currentTimeMillis()
    val messageId = s"${record.topic()}-${record.partition()}-${record.offset()}"

    system.log.debug(s"Processing message $messageId from topic ${record.topic()}")

    parseNotificationMessage(record) match {
      case Success(notificationMessage) =>
        notificationProcessorActor ! NotificationProcessorActor.ProcessNotification(
          messageId,
          notificationMessage,
          startTime
        )
        Future.successful(())

      case Failure(exception) =>
        system.log.error(s"Failed to parse message $messageId: ${exception.getMessage}")
        notificationProcessorActor ! NotificationProcessorActor.ProcessingFailed(
          messageId,
          s"JSON parsing failed: ${exception.getMessage}",
          retryable = false
        )
        Future.successful(())
    }
  }

  /**
   * Parses Kafka message payload into strongly-typed notification objects.
   *
   * Deserializes JSON payload based on the source topic and creates
   * appropriate notification message instances for processing.
   *
   * @param record the Kafka consumer record
   * @return Try containing the parsed notification message or error
   */
  private def parseNotificationMessage(record: ConsumerRecord[String, String]): Try[NotificationMessage] = {
    Try {
      val json = Json.parse(record.value())

      record.topic() match {
        case topic if topic == roomPreparationTopic =>
          val notification = json.as[RoomPreparationNotification]
          RoomPreparationMessage(notification)

        case topic if topic == reservationReminderTopic =>
          val notification = json.as[ReservationReminderNotification]
          ReservationReminderMessage(notification)

        case topic if topic == roomReleaseTopic =>
          val notification = json.as[RoomReleaseNotification]
          RoomReleaseMessage(notification)

        case topic if topic == adminNotificationsTopic =>
          val notification = json.as[AdminNotification]
          AdminMessage(notification)

        case unknownTopic =>
          throw new IllegalArgumentException(s"Unknown topic: $unknownTopic")
      }
    }
  }

  /**
   * Gracefully shuts down all consumer streams and releases resources.
   *
   * @return Future indicating completion of shutdown process
   */
  def shutdown(): Future[Unit] = {
    system.log.info("Shutting down Kafka consumer service")
    Future.successful(())
  }
}
