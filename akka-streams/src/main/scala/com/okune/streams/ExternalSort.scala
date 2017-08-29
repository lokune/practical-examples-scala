package com.okune.streams

import java.io._
import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}

object ExternalSort {
  implicit val system: ActorSystem = ActorSystem("ExternalSort")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  //sbt "akka-streams/runMain com.okune.streams.ExternalSort \"/Users/lokune/Desktop/Sort/100millionintegers.txt\" \"/Users/lokune/Desktop/Sort/sortedintegers.txt\""
  def main(args: Array[String]): Unit = {

    if(args.length < 2) throw new IllegalArgumentException("You must provide both input and output filepaths in that order.")

    val filePath = args(0)

    val outputFilePath = args(1)

    val source: Source[Long, Future[IOResult]] = FileIO.fromPath(Paths.get(filePath))
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true).map(_.utf8String.toLong))

    val sink: Sink[Any, Future[Done]] = Sink.foreach(count => println(s"$count numbers sorted"))

    val results: Future[Done] = source.via(new AccumulateStage(1000000))
      .map(sort).via(new MergeStage(outputFilePath).async).runWith(sink)

    results.onComplete(_ => system.terminate())
  }

  @inline def sort: Seq[Long] => Seq[Long] = lst => lst.sorted

  /**
    * This stage accumulates stream elements and pushes them downstream as a group
    * In this case, we are accumulating Integers as they are streamed from file in groups of `elementCount`
    *
    * @param elementCount number of elements per group
    * @tparam E element type as received from upstream
    */
  final class AccumulateStage[E](elementCount: Int) extends GraphStage[FlowShape[E, Seq[E]]] {
    val in: Inlet[E] = Inlet[E]("AccumulateStage.in")
    val out: Outlet[Seq[E]] = Outlet[Seq[E]]("AccumulateStage.out")

    override def shape: FlowShape[E, Seq[E]] = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
      var currentCount: Int = 0
      val buffer: mutable.Builder[E, Vector[E]] = Vector.newBuilder[E]

      setHandlers(in, out, new InHandler with OutHandler {
        override def onPush(): Unit = {
          val nextElement = grab(in)
          currentCount += 1
          buffer += nextElement
          if (currentCount < elementCount) {
            pull(in)
          } else {
            val result = buffer.result()
            reset()
            push(out, result)
          }
        }

        override def onPull(): Unit = {
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          val result = buffer.result()
          // Push out remaining items when upstream is finsihed
          if (result.nonEmpty) {
            emit(out, result)
          }
          reset()
          completeStage()
        }

        private def reset(): Unit = {
          buffer.clear()
          currentCount = 0
        }
      })
    }
  }

  /**
    * Write each sorted group to a temp file then merge into one sorted file
    *
    * @param outputFilePath path for the output file
    * @tparam E element type from upstream
    * @tparam F element type pushed downstream from this stage
    */
  final class MergeStage[E, F](outputFilePath: String) extends GraphStage[FlowShape[Seq[E], F]] {
    val in: Inlet[Seq[E]] = Inlet[Seq[E]]("MergeStage.in")
    val out: Outlet[F] = Outlet[F]("MergeStage.out")

    override def shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
      val lstFiles: mutable.Builder[String, Vector[String]] = Vector.newBuilder[String]
      setHandlers(in, out, new InHandler with OutHandler {
        override def onPush(): Unit = {
          val nextSeq = grab(in)
          lstFiles += toFile(nextSeq.asInstanceOf[Vector[Long]]).getAbsolutePath
          pull(in)
        }

        override def onPull(): Unit = {
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          //Merge files into one
          val pq: mutable.PriorityQueue[BinaryFileBuffer] = new mutable.PriorityQueue[BinaryFileBuffer]()
          for (filePath <- lstFiles.result()) {
            val file = new File(filePath)
            val bfb = BinaryFileBuffer(file)
            pq += bfb
          }

          val bw = new BufferedWriter(new FileWriter(outputFilePath))
          var rowCounter: Long = 0
          try {
            while (pq.nonEmpty) {
              val bfb: BinaryFileBuffer = pq.dequeue()
              bfb.pop().foreach { r =>
                bw.write(r)
                bw.newLine()
                rowCounter += 1
              }
              if (bfb.isEmpty()) {
                bfb.br.close()
                bfb.originalFile.delete()
              } else {
                pq.enqueue(bfb)
              }
            }
          } finally {
            bw.close()
            for (bfb <- pq) bfb.close()
          }
          /*Push out after upstream is finished*/
          emit(out, rowCounter.asInstanceOf[F])
          completeStage()
        }
      })
    }

    private def toFile(items: Vector[Long]): File = {
      val tmpFile = {
        val t = File.createTempFile("externalsort", "flatfile")
        t.deleteOnExit()
        val bw = new BufferedWriter(new FileWriter(t))
        try {
          for (item <- items) {
            bw.write(item.toString)
            bw.newLine()
          }
          t
        } finally {
          bw.close()
        }
      }
      tmpFile
    }
  }

}


