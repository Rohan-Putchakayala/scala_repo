package services

import javax.inject._
import play.api.Configuration
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import java.util.Properties
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.control.NonFatal
import play.api.Logging

/**
 * KafkaPublisher is responsible for producing messages directly to Kafka topics.
 *
 * This component publishes events synchronously as part of the API response flow,
 * replacing the outbox pattern for immediate event publishing.
 */
@Singleton
class KafkaPublisher @Inject()(config: Configuration)(implicit ec: ExecutionContext) extends Logging {

  /** Kafka producer properties loaded from application.conf */
  private val props = new Properties()
  props.put("bootstrap.servers", config.get[String]("kafka.bootstrap.servers"))
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("acks", "all")
  props.put("enable.idempotence", "true")
  props.put("retries", "3")
  props.put("retry.backoff.ms", "100")
  props.put("delivery.timeout.ms", "30000")
  props.put("request.timeout.ms", "10000")

  private val producer = new KafkaProducer[String, String](props)

  private val checkinTopic = config.get[String]("kafka.topic.checkin")
  private val checkoutTopic = config.get[String]("kafka.topic.checkout")

  /**
   * Publishes a visitor check-in event directly to Kafka.
   *
   * @param visitId The visit ID as the message key
   * @param payload The JSON payload containing event data
   * @return Future that completes with RecordMetadata on success
   */
  def publishCheckinEvent(visitId: Long, payload: String): Future[RecordMetadata] = {
    logger.info(s"Publishing check-in event for visitId: $visitId")
    publish(checkinTopic, visitId.toString, payload)
  }

  /**
   * Publishes a visitor check-out event directly to Kafka.
   *
   * @param visitId The visit ID as the message key
   * @param payload The JSON payload containing event data
   * @return Future that completes with RecordMetadata on success
   */
  def publishCheckoutEvent(visitId: Long, payload: String): Future[RecordMetadata] = {
    logger.info(s"Publishing check-out event for visitId: $visitId")
    publish(checkoutTopic, visitId.toString, payload)
  }

  /**
   * Publishes a message to a Kafka topic.
   *
   * @param topic Kafka topic to publish to
   * @param key   Message key (helps Kafka partitioning)
   * @param value Message payload
   * @return Future that completes with RecordMetadata on success,
   *         or fails if Kafka publishing encounters an error
   */
  private def publish(topic: String, key: String, value: String): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()

    try {
      val record = new ProducerRecord[String, String](topic, key, value)
      producer.send(record, (metadata: RecordMetadata, ex: Exception) => {
        if (ex != null) {
          logger.error(s"Failed to publish message to topic $topic with key $key", ex)
          promise.failure(ex)
        } else {
          logger.debug(s"Successfully published message to topic $topic, partition ${metadata.partition()}, offset ${metadata.offset()}")
          promise.success(metadata)
        }
      })
    } catch {
      case NonFatal(e) =>
        logger.error(s"Exception while publishing to topic $topic with key $key", e)
        promise.failure(e)
    }

    promise.future
  }

  /**
   * Flushes any pending messages and closes the Kafka producer.
   * Should be called during application shutdown.
   */
  def close(): Unit = {
    try {
      producer.flush()
      producer.close()
      logger.info("Kafka producer closed successfully")
    } catch {
      case NonFatal(e) => logger.error("Error closing Kafka producer", e)
    }
  }
}
