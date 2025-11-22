package com.room.messaging

import actors.{AdminActor, RoomServiceActor}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import services.KafkaConsumerService

import scala.concurrent.ExecutionContext

object MessagingServiceApp {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "RoomManagementMessagingServiceSystem")
    implicit val ec: ExecutionContext = system.executionContext

    val config = ConfigFactory.load()

    val roomServiceActor: ActorRef[RoomServiceActor.Command] =
      system.systemActorOf(RoomServiceActor(), "roomServiceActor")
    val adminActor: ActorRef[AdminActor.Command] =
      system.systemActorOf(AdminActor(), "adminActor")

    val kafkaConsumerService =
      new KafkaConsumerService(config, roomServiceActor, adminActor)(system, ec)

    kafkaConsumerService.startAllConsumers()

    println("Room Management Messaging service is running. Press ENTER to stop.")
    scala.io.StdIn.readLine()
    system.terminate()
  }
}
