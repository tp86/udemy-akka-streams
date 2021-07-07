package part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.concurrent.Future
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.util.Timeout

object IntegratingWithExternalServices extends App {
  implicit val system = ActorSystem("IntegratingWithExternalServices")
  // import system.dispatcher // not recommended in practice for mapAsync
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExternalService[A, B](element: A): Future[B] = ???

  // Example: PagerDuty
  case class PagerEvent(
      application: String,
      description: String,
      date:        LocalDate)

  val eventSource = Source(
    List(
      PagerEvent("AkkaInfra", "Infrastructure broke", LocalDate.now),
      PagerEvent(
        "FastDataPipeline",
        "Illegal elements in the data pipeline",
        LocalDate.now,
      ),
      PagerEvent("AkkaInfra", "A service stopped responding", LocalDate.now),
      PagerEvent("SuperFrontend", "A button doesn't work", LocalDate.now),
    ),
  )

  object PagerService {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rockthejvm.com",
    )

    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex =
        ChronoUnit.DAYS.between(
          LocalDate.EPOCH,
          pagerEvent.date,
        ) / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(
        s"Sending engineer $engineerEmail " +
          s"a high priority notification: $pagerEvent",
      )
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineerEmails =
    // mapAsync guarantess the relative order of elements, mapAsyncUnordered does not, but can be faster
    infraEvents.mapAsync(parallelism = 1)(PagerService.processEvent)
  val pagedEmailsSink = Sink.foreach[String](email =>
    println(s"Successfully sent notification to $email"),
  )

  // pagedEngineerEmails.to(pagedEmailsSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rockthejvm.com",
    )

    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex =
        ChronoUnit.DAYS.between(
          LocalDate.EPOCH,
          pagerEvent.date,
        ) / (24 * 3600) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      log.info(
        s"Sending engineer $engineerEmail " +
          s"a high priority notification: $pagerEvent",
      )
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }

    override def receive: Receive = { case pagerEvent: PagerEvent =>
      sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(
    3 seconds,
  ) // 2 or less may cause dead letters (actors are single-threaded)
  val pagerActor = system.actorOf(Props[PagerActor](), "pagerActor")
  val alternativePagedEngineersEmails =
    infraEvents.mapAsync(parallelism = 4)(event =>
      (pagerActor ? event).mapTo[String],
    )
  alternativePagedEngineersEmails.to(pagedEmailsSink).run()
}
