package modules

import javax.inject._
import play.api.inject.ApplicationLifecycle
import services.KafkaPublisher
import scala.concurrent.Future
import play.api.Logging

/**
 * Manages application lifecycle events, ensuring proper cleanup of resources
 * when the application shuts down. Specifically handles Kafka producer cleanup.
 */
@Singleton
class ApplicationLifecycleManager @Inject()(
  lifecycle: ApplicationLifecycle,
  kafkaPublisher: KafkaPublisher
) extends Logging {

  /**
   * Register shutdown hook to properly close Kafka producer
   * when the application terminates.
   */
  lifecycle.addStopHook { () =>
    logger.info("Application shutting down - cleaning up resources")

    Future.successful {
      try {
        kafkaPublisher.close()
        logger.info("Successfully closed Kafka producer")
      } catch {
        case ex: Exception =>
          logger.error("Error during Kafka producer cleanup", ex)
      }
    }
  }

  logger.info("Application lifecycle manager initialized")
}
