import actors.{ITSupportActor, SecurityActor}
import akka.actor.{ActorSystem, Props}
import services.KafkaConsumerService
import scala.concurrent.ExecutionContext.Implicits.global

object MessagingServiceApp extends App {
  val system = ActorSystem("messaging-service")

  val itSupportActor = system.actorOf(ITSupportActor.props(), "it-support-actor")
  val securityActor = system.actorOf(SecurityActor.props(), "security-actor")

  val kafkaConsumerService = new KafkaConsumerService(system, itSupportActor, securityActor)
  kafkaConsumerService.start()

  sys.addShutdownHook {
    kafkaConsumerService.stop()
    system.terminate()
  }
}