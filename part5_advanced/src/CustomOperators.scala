import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, SinkShape, SourceShape}

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App {
  implicit val system = ActorSystem("CustomOperators")

  // Example 1: custom source which emits random numbers until canceled
  class RandomNumberGenerator(max: Int)
      extends GraphStage[ /* step 0: define the shape */ SourceShape[Int]] {

    /* step 1: define the ports and the component-specific members */
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    /* step 2: construct a new shape */
    override def shape: SourceShape[Int] = SourceShape(outPort)

    /* step 3: create the logic */
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        /* step 4: define mutable state */
        /* step 5: implement logic by setting handlers on ports */
        setHandler(
          outPort,
          new OutHandler {
            // called when there is demand from downstream
            override def onPull(): Unit = {
              // emit a new element
              val nextNumber = random.nextInt(max)
              // push it out of the outPort
              push(outPort, nextNumber)
            }
          },
        )
      }
  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  // randomGeneratorSource.runWith(Sink.foreach(println))

  // Example 2: custom sink that prints elements in batches of a given size
  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("batcher")
    override def shape: SinkShape[Int] = SinkShape(inPort)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // first request (demand) on start
        override def preStart(): Unit = {
          pull(inPort)
        }

        // add mutable state always inside GraphStageLogic object!
        val batch = new mutable.Queue[Int](batchSize)
        setHandler(
          inPort,
          new InHandler {
            // called when the upstream wants to send me an element
            override def onPush(): Unit = {
              val nextElement = grab(inPort)
              batch.enqueue(nextElement)
              Thread.sleep(100) // simulate long-running computation
              if (batch.size >= batchSize) {
                println(
                  "New batch: " + batch
                    .dequeueAll(_ => true)
                    .mkString("[", ", ", "]"),
                )
              }
              pull(inPort)
            }
            override def onUpstreamFinish(): Unit = {
              if (batch.nonEmpty) {
                println(
                  "Last batch: " + batch
                    .dequeueAll(_ => true)
                    .mkString("[", ", ", "]"),
                )
                println("Stream finished.")
              }
            }
          },
        )
      }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))
  randomGeneratorSource.to(batcherSink).run()
}
