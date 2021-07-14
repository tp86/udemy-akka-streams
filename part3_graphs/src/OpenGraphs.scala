import akka.actor.ActorSystem
import akka.stream.{FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}

object OpenGraphs extends App {
  implicit val system = ActorSystem("OpenGraphs")

  /*
   * A composite source that concatenates 2 sources
   * - emits all the elements from the first source
   * - then emits all the elements from the second source
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val concat = builder.add(Concat[Int](2))

      firstSource  ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    },
  )

  // sourceGraph.runForeach(println)

  /*
   * Complex sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    },
  )

  // firstSource.runWith(sinkGraph)

  /*
   * Exercise: write your own flow that's composed of two other flows
   * - one that adds 1 to the number
   * - one that does number * 10
   */
  val flow1 = Flow[Int].map(_ + 1)
  val flow2 = Flow[Int].map(_ * 10)
  val complexFlow = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val flow1Shape = builder.add(flow1)
      val flow2Shape = builder.add(flow2)
      flow1Shape ~> flow2Shape

      FlowShape(flow1Shape.in, flow2Shape.out)
    },
  )

  // firstSource.via(complexFlow).runForeach(println)

  // Flow from sink and source
  val f =
    Flow.fromSinkAndSourceCoupled(Sink.foreach[Int](println), Source(1 to 10))
  Source(20 to 30).via(f).runForeach(println)
}
