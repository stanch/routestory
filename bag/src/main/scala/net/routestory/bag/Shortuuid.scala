package net.routestory.bag

import java.util.UUID
import java.nio.ByteBuffer
import spray.routing.PathMatcher1

object Shortuuid {
  val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
  def matcher(prefix: String): PathMatcher1[String] = s"$prefix-[$alphabet]+".r

  def make = {
    val uuid = UUID.randomUUID
    val bytes = ByteBuffer.allocate(17).put(0.asInstanceOf[Byte]).putLong(uuid.getMostSignificantBits).putLong(uuid.getLeastSignificantBits).array()
    var value = BigInt(bytes)
    var shortuuid = ""
    while (value > 0) {
      val (div, mod) = value /% alphabet.length
      shortuuid += alphabet(mod.toInt)
      value = div
    }
    shortuuid
  }
}
