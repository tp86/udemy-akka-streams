import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration._

object IntegratingWithActors extends App {
  implicit val system = ActorSystem("IntegratingWithActors")

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (n * 2)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor](), "simpleActor")

  val numbersSource = Source(1 to 10)

  /** Actor as a Flow */
  implicit val timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  // numbersSource.via(actorBasedFlow).to(Sink.ignore).run()
  // numbersSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run() // equivalent

  /** Actor as a Source */
  val actorPoweredSource: Source[Int, ActorRef] = Source.actorRef(
    completionMatcher = { case Done =>
      // complete stream immediately if we send it Done
      println("Stream completed")
      CompletionStrategy.immediately
    },
    // never fail the stream because of a message
    failureMatcher = PartialFunction.empty,
    bufferSize = 10,
    overflowStrategy = OverflowStrategy.dropHead,
  )
  val materializedActorRef = actorPoweredSource
    .to(
      Sink.foreach[Int](number =>
        println(s"Actor powered source emitted number: $number"),
      ),
    )
    .run()
  materializedActorRef ! 10
  // terminating the stream
  materializedActorRef ! Done

  /** Actor as a destination/sink
    * - an init message
    * - an ack message to confirm the reception (backpressure control)
    * - a complete message
    * - a function to generate a message in case the stream throws an exception (optional)
    */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream completed")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")
      case message =>
        log.info(s"Message $message has come to its final resting point.")
        sender() ! StreamAck
    }
  }
  val destinationActor =
    system.actorOf(Props[DestinationActor](), "destinationActor")

  val actorPoweredSink = Sink.actorRefWithBackpressure(
    destinationActor,
    ackMessage = StreamAck,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    onFailureMessage = (ex: Throwable) => StreamFail(ex),
  )

  Source(1 to 10).to(actorPoweredSink).run()
}
