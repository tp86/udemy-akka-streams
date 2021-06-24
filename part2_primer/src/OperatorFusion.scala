package part2_primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {
  implicit val system = ActorSystem("OperatorFusion")

  val simpleSource = Source(1 to 1000)
  val simpleFlow1 = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // this runs on the SAME ACTOR (single CPU core)
  // this is called operator fusion
  // this is Akka Stream default behavior and
  //     is good if operations are not time consuming
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
  // (1 to 1000).foreach(simpleActor ! _)

  // complex flows
  val complexFlow1 = Flow[Int].map { x =>
    // simulating a long computation
    Thread.sleep(1000)
    x + 1
  }
  val complexFlow2 = Flow[Int].map { x =>
    // simulating a long computation
    Thread.sleep(1000)
    x * 10
  }
  val complexFlowsGraph =
    simpleSource.via(complexFlow1).via(complexFlow2).to(simpleSink)
  // complexFlowsGraph.run() // 2 seconds pass between elements being printed

  // async boundary
  val asyncBoundariesGraph = simpleSource
    .via(complexFlow1)
    .async // flow up to this point runs on one actor
    .via(complexFlow2)
    .async // flow between async calls runs on separate actor
    .to(simpleSink) // this runs on yet another actor
  // asyncBoundariesGraph.run()

  // ordering guarantees
  Source(1 to 3)
    .map(element => { println(s"Flow A: $element"); element })
    .async
    .map(element => { println(s"Flow B: $element"); element })
    .async
    .map(element => { println(s"Flow C: $element"); element })
    .async
    .runWith(Sink.ignore)
}
