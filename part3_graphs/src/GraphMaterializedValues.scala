package part3_graphs

import akka.actor.ActorSystem
import akka.stream.SinkShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {
  implicit val system = ActorSystem("GraphMaterializedValues")

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /** A composite component (sink)
    * - prints out all strings that are lowercase
    * - counts strings that are short (< 5 chars)
    */
  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(printer, counter)((_, counterMat) => counterMat) {
      implicit builder => (printerShape, counterShape) =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[String](2))
        val lowercaseFilter =
          builder.add(
            Flow[String].filter(string => string == string.toLowerCase),
          )
        val shortFilter = builder.add(Flow[String].filter(_.length < 5))

        broadcast ~> lowercaseFilter ~> printerShape
        broadcast ~> shortFilter     ~> counterShape

        SinkShape(broadcast.in)
    },
  )

  val shortWordsCountFuture =
    wordSource.toMat(complexWordSink)(Keep.right).run()
  import system.dispatcher
  shortWordsCountFuture.onComplete {
    case Success(count) =>
      println(s"The total number of short words is: $count")
    case Failure(_) =>
      println("Counting the total number of short words failed")
  }
}
