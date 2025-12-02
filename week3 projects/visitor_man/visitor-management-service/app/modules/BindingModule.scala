package modules

import play.api.inject._
import play.api.{Configuration, Environment}

/**
 * Guice module that registers application-wide bindings.
 * Updated to remove outbox pattern dependencies as we now publish directly to Kafka.
 */
class BindingModule extends Module {
  /**
   * No specific bindings needed as all services are managed by Play's DI.
   * KafkaPublisher will be eagerly initialized when injected into VisitorService.
   */
  override def bindings(env: Environment, conf: Configuration) = Seq(
    bind[ApplicationLifecycleManager].toSelf.eagerly()
  )
}
