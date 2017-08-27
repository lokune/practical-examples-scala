package com.okune.streams

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

object MassiveFileCreator {
  implicit val system: ActorSystem = ActorSystem("MassiveFileCreator")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  //sbt "akka-streams/runMain com.okune.streams.MassiveFileCreator \"/Users/lokune/Desktop/Sort/100millionintegers.txt\""
  def main(args: Array[String]): Unit = {

    if (args.length < 1) throw new IllegalArgumentException("You must provide output filepath")

    val filePath: String = args(0)
    val source: Source[Int, NotUsed] = Source.fromIterator(() => Iterator.continually(Random.nextInt(100000000)).take(100000000))

    def lineSink(filePath: String): Sink[String, Future[IOResult]] =
      Flow[String]
        .map(s => ByteString(s + "\n"))
        .toMat(FileIO.toPath(Paths.get(filePath)))(Keep.right)


    val results = source.map(_.toString).runWith(lineSink(filePath))

    results.onComplete(_ => system.terminate())
  }
}
