import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith,
}
import akka.stream.{ClosedShape, FanOutShape2, UniformFanInShape}

import java.time.LocalDateTime

object MoreOpenGraphs extends App {
  implicit val system = ActorSystem("MoreOpenGraphs")

  /** Example: Max3 operator
    * - 3 inputs of type Int
    * - whenever there is a value for all 3 inputs, return the maximum value of the 3
    */

  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val max1 = builder.add(ZipWith[Int, Int, Int](Math.max))
    val max2 = builder.add(ZipWith[Int, Int, Int](Math.max))

    max1.out ~> max2.in0

    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source.repeat(5).take(10)
  val source3 = Source(10 to 1 by -1)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max3Shape = builder.add(max3StaticGraph)

      source1   ~> max3Shape
      source2   ~> max3Shape
      source3   ~> max3Shape
      max3Shape ~> maxSink

      ClosedShape
    },
  )

  // max3RunnableGraph.run()

  // same for UniformFanOutShape

  /** Non-uniform fan out shape
    *
    * Processing bank transactions
    * Txn suspicious if amount > 10000
    *
    * Streams component for txns
    * - output1: let the transaction go through unmodified
    * - output2: suspicious txn ids
    */

  case class Transaction(
      id:        String,
      source:    String,
      recipient: String,
      amount:    Int,
      date:      LocalDateTime)

  val transactionSource = Source(
    List(
      Transaction("14897369", "Paul", "Jim", 100, LocalDateTime.now),
      Transaction("09184731", "Daniel", "Jim", 100000, LocalDateTime.now),
      Transaction("32498991", "Jim", "Alice", 7000, LocalDateTime.now),
    ),
  )

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService =
    Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTransactionStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnIdExtractor =
      builder.add(Flow[Transaction].filter(_.amount > 10000).map[String](_.id))
    broadcast ~> suspiciousTxnIdExtractor
    new FanOutShape2(
      broadcast.in,
      broadcast.out(1),
      suspiciousTxnIdExtractor.out,
    )
  }
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val suspiciousTxnShape = builder.add(suspiciousTransactionStaticGraph)
      transactionSource       ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService
      ClosedShape
    },
  )

  suspiciousTxnRunnableGraph.run()
}
