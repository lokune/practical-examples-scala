package com.okune.streams

import java.io.{BufferedReader, EOFException, File, FileReader}

class BinaryFileBuffer(f: File, bufferSize: Int = 2048) {
  val originalFile: File = f
  val br: BufferedReader = new BufferedReader(new FileReader(f), bufferSize)

  private[this] var cache = readLine()

  private[this] def readLine(): Option[String] = try {
    // Option(null) resolves to None
    Option(br.readLine())
  } catch {
    case _: EOFException =>
      None
  }

  def isEmpty(): Boolean = cache.isEmpty

  def close(): Unit = br.close()

  def peek(): Option[String] = cache

  def pop(): Option[String] = {
    val seen = peek()
    cache = readLine()
    seen
  }
}

object BinaryFileBuffer {
  def apply(f: File) = new BinaryFileBuffer(f)

  implicit def ordering[A <: BinaryFileBuffer]: Ordering[A] =
    Ordering.by(_.peek().get.toLong)
}