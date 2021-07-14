import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}

object FirstPrinciples extends App {

  implicit val system = ActorSystem("FirstPrinciples")

  // sources
  val source = Source(1 to 10)

  // sinks
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
  //graph.run()

  // flows transform elements
  val flow = Flow[Int].map(_ + 1)
  val sourceWithFlow = source.via(flow)
  val sinkWithFlow = flow.to(sink)

  val graph2 = sourceWithFlow.to(sink)
  //graph2.run()
  //source.to(sinkWithFlow).run()
  //source.via(flow).to(sink).run()

  // nulls are NOT allowed
  //val illegalSource = Source.single[String](null)
  //illegalSource.to(Sink.foreach(println)).run()
  // use Options instead

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(LazyList.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  val futureSource = Source.future(Future(42))

  // sinks
  val theMostBoringSink = Sink.ignore // does nothing with elements
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves the head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)(_ + _)

  // flows
  val mapFlow = Flow[Int].map(_ * 2)
  val takeFlow = Flow[Int].take(5)
  // drop, filter, ...
  // does NOT have flatMap - does not seem to be up-to-date, e.g flatMapConcat, flatMapMerge, ...

  // source -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  //doubleFlowGraph.run()

  // syntactic sugars
  val mapSource =
    Source(1 to 10).map(_ * 2) // Source(1 to 10).via(Flow[Int].map(_ * 2))
  // run streams directly
  //mapSource.runForeach(println) // mapSource.to(Sink.foreach(println)).run()

  /** Exercise: create a stream that takes the names of persons, then keep the first 2 names with length > 5 characters.
    */
  val names = Source(List("name", "John Doe", "Jane Doe", "John Doe Jr."))
  val filterLength = Flow[String].filter(_.length > 5)
  val limit = Flow[String].take(2)
  val namesStream = names.via(filterLength).via(limit).to(foreachSink)
  namesStream.run()
}
