import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.scaladsl.Flow
import akka.stream.OverflowStrategy

object BackpressureBasics extends App {
  implicit val system = ActorSystem("BackpressureBasics")

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  // fastSource.to(slowSink).run() // fusing, not backpressure

  // fastSource.async.to(slowSink).run() // backpressure

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  // fastSource.async.via(simpleFlow).async.to(slowSink).run()

  /** reactions to backpressure (in order):
    * - try to slow down if possible
    * - buffer elements until there's more demand
    * - drop down elements from the buffer if it overloads
    * - tear down/kill the whole stream (failure)
    */

  val bufferedFlow =
    simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow)
    .async
    .to(slowSink)
  // .run()
  // flow buffers 10 numbers (last), but sink also buffers (first 16 numbers)
  /** 1-16: nobody is backpressured
    * 17-26: flow will buffer, flow will start dropping at the next element
    * 26-1000: flow will always drop the last element from 10-elements buffer
    * 991-1000 => map and buffered for sink to consume
    */

  /** overflow strategies:
    * - drop head = oldest
    * - drop tail = newest
    * - drop new = exact element to be added = keeps the buffer
    * - drop the entire buffer
    * - emit backpressure signal
    * - fail
    */

  // throttling
  import scala.concurrent.duration._
  fastSource
    .throttle(2, 1 second) // emits (at most) 2 elements per second
    .runForeach(println)
}
