package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object MaterializingStreams extends App {
  implicit val system = ActorSystem("MaterializingStreams")
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  //val simpleMaterializedValue = simpleGraph.run() // NotUsed

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int](_ + _)
  //val sumFuture = source.runWith(sink) // runWith <=> source.toMat(sink)(Keep.right).run()
  //sumFuture.onComplete {
  //case Success(value) => println(s"The sum of all elements is: $value")
  //case Failure(ex) => println(s"The sum of the elements could not be computed: $ex")
  //}

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink =
    Sink.foreach[Int](println) // materialized value: Future[Done]
  val graph =
    simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  //graph.run().onComplete {
  //case Success(value) =>
  //println(s"Stream processing finished with value: $value")
  //case Failure(ex) => println(s"Stream processing failed with: $ex")
  //}

  // sugars
  //Source(1 to 10).runWith(Sink.reduce(_ + _)) // source(...).to(sink...).run()
  //Source(1 to 10).runReduce(_ + _) // same as above

  // backwards
  //val value = Sink.foreach[Int](println).runWith(Source.single(42)) // NotUsed
  // both ways
  //Flow[Int].map(_ * 2).runWith(simpleSource, simpleSink)
}
