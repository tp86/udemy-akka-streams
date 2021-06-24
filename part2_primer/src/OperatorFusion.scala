package part2_primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system = ActorSystem("OperatorFusion")

  val simpleSource = Source(1 to 1000)
  val simpleFlow1 = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // this runs on the SAME ACTOR (single CPU core) - this is called operator fusion
  simpleSource.via(simpleFlow1).via(simpleFlow2).to(simpleSink)

  // "equivalent" behavior
  class SimpleActor extends Actor {
    override def receive: Receive = { case x: Int =>
      // flow operations
      val y = x + 1
      val z = y * 10
      // sink operation
      println(z)
    }
  }
  val simpleActor = system.actorOf(Props[SimpleActor]())
  (1 to 1000).foreach(simpleActor ! _)

}
