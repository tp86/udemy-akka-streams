import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {
  implicit val system = ActorSystem("Substreams")
  import system.dispatcher

  // Example 1 - grouping a stream by a certain function
  // group words by their first letter case-insensitive
  val wordSource = Source(
    List("Akka", "is", "amazing", "learning", "substreams"),
  )
  val groups = wordSource.groupBy(
    30, // up to how many groups can be created
    word => if (word.isEmpty) '\u0000' else word.toLowerCase()(0),
  )

  groups
    .to(Sink.fold(0)((count, word) => {
      val newCount = count + 1
      println(s"I just received $word, count is $newCount")
      newCount
    }))
    .run()

  // Example 2 - merge substreams back
  // Useful for MapReduce scenario
  val textSource = Source(
    List("I love Akka Streams", "this is amazing", "learning from Rock the JVM"),
  )

  val totalCharCountFuture = textSource
    .groupBy(2, string => string.length % 2) // Map part
    .map(
      _.length,
    ) // do your expensive computation here - run in parallel in 2 groups
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right) // Reduce part
    .run()
  totalCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count: $value")
    case Failure(exception) => println(s"Char computation failed: $exception")
  }

  // Example 3 - splitting a stream into substreams when a condition is met
  val text = """I love Akka Streams
               |this is amazing
               |learning from Rock the JVM
               |""".stripMargin

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(_ == '\n')
    .filter(c => c != '\n' && c != '\r')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()
  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count alternative: $value")
    case Failure(exception) => println(s"Char computation failed: $exception")
  }

  // Example 4 - flattening
  val simpleSource = Source(1 to 5)
  simpleSource
    .flatMapConcat(x => Source(x to (3 * x)))
    .runWith(Sink.foreach(println))
  simpleSource
    .flatMapMerge(2, x => Source(x to (3 * x)))
    .runWith(Sink.foreach(println))
}
