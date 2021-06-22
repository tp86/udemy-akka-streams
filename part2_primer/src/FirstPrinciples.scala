package part2_primer

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
}
