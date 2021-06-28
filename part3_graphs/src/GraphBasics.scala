package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ClosedShape
import akka.stream.scaladsl.{
  Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip,
}

object GraphBasics extends App {
  implicit val system = ActorSystem("GraphBasics")

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1) // hard computation
  val multiplier = Flow[Int].map(_ * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // step 2 - add the necessary, auxiliary shapes for this graph
      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int]())

      // step 3 - tying up the components/shapes
      input     ~> broadcast
      broadcast ~> incrementer ~> zip.in0
      broadcast ~> multiplier  ~> zip.in1
      zip.out   ~> output

      // step 4 - return the shape
      ClosedShape
    }, // graph
  ) // runnable graph

  // graph.run()

  // exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
  val sink1 = Sink.foreach[Int](x => println(s"Sink 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink 2: $x"))
  val multiSinkGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Int](2))

      input     ~> broadcast
      broadcast ~> sink1
      broadcast ~> sink2

      ClosedShape
    },
  )

  // multiSinkGraph.run()

  // exercise 2: build a graph from diagram
  import scala.concurrent.duration._
  val fastSource = Source(1 to 1000).throttle(5, 1 second)
  val slowSource = Source(1001 to 2000).throttle(2, 1 second)
  val balancingGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge
      slowSource ~> merge
      merge      ~> balance
      balance    ~> sink1
      balance    ~> sink2

      ClosedShape
    },
  )

  balancingGraph.run()
}
