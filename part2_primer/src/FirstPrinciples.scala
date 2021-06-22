package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}

object FirstPrinciples extends App {

  implicit val system = ActorSystem("FirstPrinciples")

  // sources
  val source = Source(1 to 10)

  // sinks
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
  graph.run()
}
