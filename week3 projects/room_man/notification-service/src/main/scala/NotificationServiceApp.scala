import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import actors.NotificationProcessorActor
import services.{EmailService, KafkaConsumerService}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Main application entry point for the Notification Service microservice.
 *
 * This service is responsible for consuming Kafka messages from the room management
 * service and processing various types of notifications including email reminders,
 * room preparation notifications, and administrative events.
 *
 * The application bootstraps the Akka actor system, initializes required services,
 * and starts the Kafka consumer streams for reliable message processing.
 */
object NotificationServiceApp extends App {

  /**
   * Root actor behavior that supervises all notification service components.
   *
   * Creates and manages the notification processor actor and Kafka consumer service,
   * providing centralized supervision and lifecycle management for the application.
   */
  object RootActor {
    sealed trait Command
    case object StartApplication extends Command
    case object StopApplication extends Command

    /**
     * Creates the root actor behavior with dependency injection and service initialization.
     *
     * @return Behavior that manages the notification service application lifecycle
     */
    def apply(): Behavior[Command] = Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val ec: ExecutionContext = context.executionContext

      val config = ConfigFactory.load()

      context.log.info("Initializing Notification Service components...")

      val emailService = new EmailService(config)
      val notificationProcessorActor = context.spawn(
        NotificationProcessorActor(emailService),
        "notification-processor"
      )

      val kafkaConsumerService = new KafkaConsumerService(
        config,
        notificationProcessorActor
      )

      context.log.info("Notification Service initialized successfully")

      Behaviors.receiveMessage {
        case StartApplication =>
          context.log.info("Starting Notification Service...")

          kafkaConsumerService.startConsumers().onComplete {
            case Success(_) =>
              context.log.info("Kafka consumers started successfully")
            case Failure(exception) =>
              context.log.error("Failed to start Kafka consumers", exception)
              context.self ! StopApplication
          }

          Behaviors.same

        case StopApplication =>
          context.log.info("Shutting down Notification Service...")

          kafkaConsumerService.shutdown().onComplete {
            case Success(_) =>
              context.log.info("Notification Service shutdown completed")
              context.system.terminate()
            case Failure(exception) =>
              context.log.error("Error during shutdown", exception)
              context.system.terminate()
          }

          Behaviors.same
      }
    }
  }

  /**
   * Application entry point that creates the actor system and starts the service.
   *
   * Configures logging, creates the root actor, and handles graceful shutdown
   * on system termination signals.
   */
  def main(): Unit = {
    val system = ActorSystem(RootActor(), "notification-service")
    implicit val ec: ExecutionContext = system.executionContext

    system.log.info("=" * 60)
    system.log.info("    Room Management Notification Service")
    system.log.info("    Version: 1.0.0")
    system.log.info("    Akka Version: 2.8.5")
    system.log.info("=" * 60)

    val rootActor = system

    rootActor ! RootActor.StartApplication

    sys.addShutdownHook {
      system.log.info("Received shutdown signal, initiating graceful shutdown...")
      rootActor ! RootActor.StopApplication
    }

    system.whenTerminated.onComplete {
      case Success(_) =>
        println("Notification Service terminated successfully")
      case Failure(exception) =>
        println(s"Notification Service terminated with error: ${exception.getMessage}")
        System.exit(1)
    }
  }

  main()
}
