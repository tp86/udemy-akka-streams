package part4_techniques

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.time.LocalDate

object AdvancedBackpressure extends App {
  implicit val system = ActorSystem("AdvancedBackpressure")

  // controlling backpressure with .buffer()
  val controlledFlow = Flow[Int]
    .map(_ * 2)
    .buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(
      description: String,
      date:        LocalDate,
      nInstances:  Int = 1)
  case class Notification(
      email:      String,
      pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", LocalDate.now),
    PagerEvent("Illegal elements in the data pipeline", LocalDate.now),
    PagerEvent("Number of HTTP 5xx spiked", LocalDate.now),
    PagerEvent("A service stopped responding", LocalDate.now),
  )
  val eventSource = Source(events)

  val oncallEngineer = "daniel@rockthejvm.com"

  def sendEmail(notification: Notification) = println(
    s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}",
  )

  val notificationSink =
    Flow[PagerEvent]
      .map(event => Notification(oncallEngineer, event))
      .to(Sink.foreach[Notification](sendEmail))

  // eventSource.to(notificationSink).run()

  /** un-backpressurable source */
  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(
      s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}",
    )
  }

  // alternative to backpressure - upstream and downstream rates decoupled
  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((event1, event2) => {
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(
        s"You have $nInstances events that require your attention",
        LocalDate.now,
        nInstances,
      )
    }) // conflate will aggregate elements and emit aggregated element when there is downstream demand
    .map(Notification(oncallEngineer, _))

  eventSource
    .via(aggregateNotificationFlow)
    .async
    .to(Sink.foreach[Notification](sendEmailSlow))
  //.run()

  /** Slow producers - extrapolate/expand */
  import scala.concurrent.duration._
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

  slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))
}
