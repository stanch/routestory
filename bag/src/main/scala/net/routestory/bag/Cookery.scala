package net.routestory.bag

import org.parboiled.common.Base64
import scala.util.Try
import java.security.MessageDigest

object Cookery {
  // TODO: store in config?
  val secret = "as;douh[0823[08s;oduhfu0w8efius;ihd;fouywqe'[oiu"

  def encode(value: String) = {
    val content = Base64.rfc2045.encodeToString(value.getBytes, false)
    val timestamp = System.currentTimeMillis.toString
    val signed = signature(content, timestamp)
    List(content, timestamp, signed).mkString("|")
  }

  def decode(value: String) = Try {
    val Array(content, timestamp, signed) = value.split('|')
    assert(signed == signature(content, timestamp))
    assert(timestamp.toLong > System.currentTimeMillis - 31 * 86400000L)
    new String(Base64.rfc2045.decode(content))
  }.toOption

  def signature(parts: String*): String = {
    val md = MessageDigest.getInstance("SHA1")
    md.update(secret.getBytes)
    parts.foreach(p ⇒ md.update(p.getBytes))
    md.digest().map("%02X" format _).mkString
  }
}
