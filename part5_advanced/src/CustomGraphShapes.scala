import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Balance, GraphDSL, Merge, RunnableGraph, Sink, Source,
}
import akka.stream.{ClosedShape, Graph, Inlet, Outlet, Shape}

import scala.concurrent.duration._

object CustomGraphShapes extends App {
  implicit val system = ActorSystem("CustomGraphShapes")

  // balance 2x3 shape
  case class Balance2x3(
      in0:  Inlet[Int],
      in1:  Inlet[Int],
      out0: Outlet[Int],
      out1: Outlet[Int],
      out2: Outlet[Int])
      extends Shape {

    override val inlets: Seq[Inlet[_]] = List(in0, in1)

    override val outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy(),
    )

  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2),
    )
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[sink $index] Received $element, current count is $count")
        count + 1
      })

      val sinks = Vector.tabulate(3)(createSink(_))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource      ~> balance2x3.in0
      fastSource      ~> balance2x3.in1
      balance2x3.out0 ~> sinks(0)
      balance2x3.out1 ~> sinks(1)
      balance2x3.out2 ~> sinks(2)

      ClosedShape
    },
  )
  // balance2x3Graph.run()

  /** Exercise: generalize balance component
    * - make it M x N
    * - work with generic type
    */
  case class BalanceMxN[T](
      override val inlets:  Seq[Inlet[T]],
      override val outlets: Seq[Outlet[T]])
      extends Shape {

    override def deepCopy(): Shape =
      BalanceMxN(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))
  }

  object BalanceMxN {
    def apply[T](
        inputs:  Int,
        outputs: Int,
      ): Graph[BalanceMxN[T], NotUsed] = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[T](inputs))
      val balance = builder.add(Balance[T](outputs))

      merge ~> balance

      BalanceMxN(merge.inlets, balance.outlets)
    }
  }

  val balanceMxNGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
      val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"[sink $index] Received $element, current count is $count")
        count + 1
      })

      val sinks = Vector.tabulate(3)(createSink(_))

      val balanceMxN = builder.add(BalanceMxN[Int](2, 3))

      slowSource ~> balanceMxN.inlets(0)
      fastSource ~> balanceMxN.inlets(1)
      balanceMxN.outlets.indices.foreach(i => balanceMxN.outlets(i) ~> sinks(i))

      ClosedShape
    },
  )
  balanceMxNGraph.run()
}
