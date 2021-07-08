package part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{ActorAttributes, RestartSettings, Supervision}

import scala.util.Random

object FaultTolerance extends App {
  implicit val system = ActorSystem("FaultTolerance")

  val faultySource =
    Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)

  // 1 - logging
  faultySource
    .log("trackingElements")
    .to(Sink.ignore)
  // .run()

  // 2 - gracefully terminating a stream
  faultySource
    .recover { case _: RuntimeException => Int.MinValue }
    .log("gracefulSource")
    .to(Sink.ignore)
  // .run()

  // 3 - recover with another stream
  faultySource
    .recoverWithRetries(
      3,
      { case _: RuntimeException =>
        Source(90 to 99)
      },
    )
    .log("recoverWithRetries")
    .to(Sink.ignore)
  // .run()

  // 4 - backoff supervision
  import scala.concurrent.duration._
  val restartSource = RestartSource.onFailuresWithBackoff(
    RestartSettings(
      minBackoff = 1 second,
      maxBackoff = 30 seconds,
      randomFactor = 0.2,
    ),
  ) { () =>
    {
      val randomNumber = Random.nextInt(20)
      Source(1 to 10).map(e =>
        if (e == randomNumber) throw new RuntimeException else e,
      )
    }
  }
  restartSource
    .log("restartBackoff")
    .to(Sink.ignore)
  // .run()

  // 5 - supervision strategy
  val numbers = Source(1 to 20)
    .map(n => if (n == 13) throw new RuntimeException else n)
    .log("supervision")
  val supervisedNumbers =
    numbers.withAttributes(ActorAttributes.supervisionStrategy {
      /** RESUME - skips the faulty element
        * STOP - stop the stream
        * RESTART - RESUME + clears internal state (folds, counts, etc.)
        */
      case _: RuntimeException => Supervision.Resume
      case _ => Supervision.Stop
    })
  supervisedNumbers
    .to(Sink.ignore)
    .run()
}
