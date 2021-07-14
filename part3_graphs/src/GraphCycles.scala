import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source,
  Zip, ZipWith,
}
import akka.stream.{ClosedShape, OverflowStrategy, UniformFanInShape}

object GraphCycles extends App {
  implicit val system = ActorSystem("GraphCycles")

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    // format: off
    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape
    // format: on

    ClosedShape
  }

  RunnableGraph
    .fromGraph(accelerator)
  // .run() // prints only first one, then components are quickly backpressured
  // graph cycle deadlock

  /** Solution 1: MergePreferred */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    // format: off
    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape.preferred <~ incrementerShape
    // format: on

    ClosedShape
  }

  RunnableGraph
    .fromGraph(actualAccelerator)
  // .run()

  /** Solution 2: buffers
    */
  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape =
      builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
        println(s"Accelerating $x")
        Thread.sleep(100)
        x
      })

    // format: off
    sourceShape ~> mergeShape ~> repeaterShape
                   mergeShape <~ repeaterShape
    // format: on

    ClosedShape
  }

  // RunnableGraph.fromGraph(bufferedRepeater).run()

  /** cycles risk deadlocking
    * - add bounds to the number of elements in the cycle
    *
    * boundedness vs liveness (capacity of the graph to not deadlock)
    */

  /** Challenge: create a fan-in shape
    * - two inputs which will be fed with EXACTLY ONE number (1 and 1)
    * - output will emit an INFINITE SEQUENCE based off those 2 numbers
    * 1, 2, 3, 5, 8, ...
    *
    * Hint: use ZipWith and cycles, MergePreferred
    */

  val fibonacciStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge1 = builder.add(MergePreferred[BigInt](1))
    val merge2 = builder.add(MergePreferred[BigInt](1))
    val inputBroadcast = builder.add(Broadcast[BigInt](2))
    val outputBroadcast = builder.add(Broadcast[BigInt](2))
    val adder = builder.add(ZipWith[BigInt, BigInt, BigInt](_ + _))

    merge1 ~> adder.in0
    merge2 ~> inputBroadcast ~> adder.in1
    merge1.preferred <~ inputBroadcast
    adder.out ~> outputBroadcast
    merge2.preferred <~ outputBroadcast

    UniformFanInShape(outputBroadcast.out(1), merge1.in(0), merge2.in(0))
  }

  val source1 = Source.single[BigInt](1)
  val source2 = Source.single[BigInt](1)
  val fibonacciGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val fibonacci = builder.add(fibonacciStaticGraph)
      source1       ~> fibonacci.in(0)
      source2       ~> fibonacci.in(1)
      fibonacci.out ~> Sink.foreach[BigInt](println)

      ClosedShape
    },
  )
  fibonacciGraph.run()

  // "offical" solution
  val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[BigInt, BigInt]())
    val mergePreferred = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fiboLogic = builder.add(Flow[(BigInt, BigInt)].map {
      case (last, previous) =>
        (last + previous, last)
    })
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    // format: off
    zip.out ~> mergePreferred ~> fiboLogic ~> broadcast ~> extractLast
               mergePreferred.preferred    <~ broadcast
    // format: on

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1 = builder.add(Source.single[BigInt](1))
      val source2 = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))
      val fibo = builder.add(fibonacciGenerator)

      source1  ~> fibo.in(0)
      source2  ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape
    },
  )

  // fiboGraph.run()
}
