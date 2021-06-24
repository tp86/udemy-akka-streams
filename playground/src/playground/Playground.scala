package playground

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}

object Playground extends App {

  implicit val actorSystem = ActorSystem("Playground")

  Source.single("Hello, Streams!").to(Sink.foreach(println)).run()
}
