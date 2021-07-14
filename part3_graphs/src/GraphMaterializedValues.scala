import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, SinkShape}

import scala.concurrent.Future
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

  import system.dispatcher
  val shortWordsCountFuture =
    wordSource.toMat(complexWordSink)(Keep.right).run()
  shortWordsCountFuture.onComplete {
    case Success(count) =>
      println(s"The total number of short words is: $count")
    case Failure(_) =>
      println("Counting the total number of short words failed")
  }

  /** Exercise: modify flow to return count of elements that passed through it in its materialized value
    */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, A](0)((count, _) => count + 1)
    Flow.fromGraph(
      GraphDSL.create(counterSink) { implicit builder => sinkShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[A](2))
        val flowShape = builder.add(flow)

        broadcast ~> sinkShape
        broadcast ~> flowShape

        FlowShape(broadcast.in, flowShape.out)
      },
    )
  }

  val simpleFlow = Flow[Int].map(identity)
  val simpleSource = Source(1 to 42)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture =
    simpleSource
      .viaMat(enhanceFlow(simpleFlow))(Keep.right)
      .to(simpleSink)
      .run()
  enhancedFlowCountFuture.onComplete {
    case Success(count) => println(s"$count elements went through flow")
    case _ => println("Something failed")
  }
}
