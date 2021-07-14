import akka.actor.ActorSystem
import akka.stream.KillSwitches
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}

import scala.concurrent.duration._

object DynamicStreamHandling extends App {
  implicit val system = ActorSystem("DynamicStreamHandling")
  // import system.dispatcher

  // Kill Switch
  val killSwitchFlow = KillSwitches.single[Int]
  val counter = Source(LazyList.from(1)).throttle(1, 1 second).log("counter")
  val sink = Sink.ignore

  val killSwitch = counter
    .viaMat(killSwitchFlow)(Keep.right)
    .to(sink)
  // .run()

  // system.scheduler.scheduleOnce(3 seconds) {
  // killSwitch.shutdown()
  // }

  // Shared Kill Switch
  val anotherCounter =
    Source(LazyList.from(1)).throttle(2, 1 second).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  counter
    .via(sharedKillSwitch.flow)
  // .runWith(Sink.ignore)
  anotherCounter
    .via(sharedKillSwitch.flow)
  // .runWith(Sink.ignore)

  // system.scheduler.scheduleOnce(3 seconds) {
  // sharedKillSwitch.shutdown()
  // }

  // MergeHub

  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // materializedSink acts as a (shared) sink it was connected to
  // use this sink any times you like
  // can be connected to at runtime
  // Source(1 to 10).runWith(materializedSink)
  // counter.runWith(materializedSink)

  // BroadcastHub

  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  // materializedSource.runWith(Sink.ignore)
  // materializedSource.runWith(Sink.foreach[Int](println))

  /** Challenge - combine a mergeHub and a broadcastHub
    *
    * A publisher-subscriber component
    */
  val merge = MergeHub.source[String]
  val bcast = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = merge.toMat(bcast)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(e => println(s"I received: $e")))
  subscriberPort
    .map(string => string.length)
    .to(Sink.foreach(n => println(s"I got a number: $n")))
    .run()

  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "Scala")).to(publisherPort).run()
  Source.single("STREEEEEEAMS").runWith(publisherPort)
}
