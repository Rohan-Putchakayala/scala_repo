package services

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import play.api.Configuration
import play.api.libs.json.{Json, Writes}
import models._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

/**
 * Kafka producer service responsible for publishing reservation-related events
 * to various topics for consumption by notification and other downstream services.
 *
 * Features idempotent configuration and Play JSON serialization integration.
 * Can be disabled via configuration for development environments.
 */
@Singleton
class KafkaProducerService @Inject()(config: Configuration)(implicit ec: ExecutionContext) {

  private val kafkaBootstrapServers: String =
    config.getOptional[String]("kafka.bootstrap.servers").getOrElse("localhost:9092")
  private val kafkaEnabled: Boolean =
    config.getOptional[Boolean]("kafka.enabled").getOrElse(true)

  private val roomPreparationTopic: String =
    config.getOptional[String]("kafka.topics.room.preparation").getOrElse("room-preparation-notifications")
  private val reservationReminderTopic: String =
    config.getOptional[String]("kafka.topics.reservation.reminder").getOrElse("reservation-reminder-notifications")
  private val roomReleaseTopic: String =
    config.getOptional[String]("kafka.topics.room.release").getOrElse("room-release-notifications")
  private val adminNotificationsTopic: String =
    config.getOptional[String]("kafka.topics.admin.notifications").getOrElse("admin-notifications")

  private val producerConfig = Map[String, Object](
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafkaBootstrapServers,
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> classOf[StringSerializer].getName,
    ProducerConfig.ACKS_CONFIG -> config.getOptional[String]("kafka.producer.acks").getOrElse("all"),
    ProducerConfig.RETRIES_CONFIG -> config.getOptional[Int]("kafka.producer.retries").getOrElse(3).toString,
    ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION ->
      config.getOptional[Int]("kafka.producer.max.in.flight.requests.per.connection").getOrElse(1).toString,
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG ->
      config.getOptional[Boolean]("kafka.producer.enable.idempotence").getOrElse(true).toString
  ).asJava

  private val producer: Option[KafkaProducer[String, String]] =
    if (kafkaEnabled) Some(new KafkaProducer[String, String](producerConfig)) else None

  /**
   * Publishes a message to the specified Kafka topic with JSON serialization.
   *
   * @param topic the Kafka topic to publish to
   * @param key the message key for partitioning
   * @param message the message payload to serialize as JSON
   * @param writes implicit JSON writer for the message type
   * @return Future containing the record metadata or null if Kafka is disabled
   */
  def sendMessage[T](topic: String, key: String, message: T)(implicit writes: Writes[T]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    val jsonMessage = Json.toJson(message).toString()
    val record = new ProducerRecord[String, String](topic, key, jsonMessage)

    producer match {
      case Some(p) =>
        p.send(record, (metadata: RecordMetadata, exception: Exception) => {
          if (exception != null) {
            promise.failure(exception)
          } else {
            promise.success(metadata)
          }
        })
      case None =>
        promise.success(null.asInstanceOf[RecordMetadata])
    }

    promise.future
  }

  /**
   * Publishes room preparation notification for facilities team to prepare the room.
   *
   * @param notification the room preparation notification containing reservation details
   * @return Future containing the record metadata
   */
  def sendRoomPreparationNotification(notification: RoomPreparationNotification): Future[RecordMetadata] = {
    sendMessage(roomPreparationTopic, s"room-prep-${notification.reservationId}", notification)
  }

  /**
   * Publishes reservation reminder notification for attendee before meeting time.
   *
   * @param notification the reservation reminder notification
   * @return Future containing the record metadata
   */
  def sendReservationReminder(notification: ReservationReminderNotification): Future[RecordMetadata] = {
    sendMessage(reservationReminderTopic, s"reminder-${notification.reservationId}", notification)
  }

  /**
   * Publishes room release notification when a reservation is auto-released due to no-show.
   *
   * @param notification the room release notification
   * @return Future containing the record metadata
   */
  def sendRoomReleaseNotification(notification: RoomReleaseNotification): Future[RecordMetadata] = {
    sendMessage(roomReleaseTopic, s"release-${notification.reservationId}", notification)
  }

  /**
   * Publishes administrative notification for audit trails, dashboards, and alerts.
   *
   * @param notification the administrative notification
   * @return Future containing the record metadata
   */
  def sendAdminNotification(notification: AdminNotification): Future[RecordMetadata] = {
    sendMessage(adminNotificationsTopic, s"admin-${notification.reservationId}", notification)
  }

  /**
   * Gracefully closes the Kafka producer and releases resources.
   */
  def close(): Unit = {
    producer.foreach(_.close())
  }
}
