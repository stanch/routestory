package net.routestory.lounge2

import scala.concurrent.Promise
import com.loopj.android.http._
import scala.util._
import play.api.libs.json._
import java.io.File
import scala.collection.JavaConversions._

case class PlainHandler(promise: Promise[String]) extends AsyncHttpResponseHandler {
  override def onSuccess(response: String) {
    promise.complete(Success(response))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}

case class JsonHandler[A: Reads](promise: Promise[A]) extends AsyncHttpResponseHandler {
  override def onSuccess(response: String) {
    promise.complete(Json.fromJson[A](Json.parse(response)).fold(
      invalid = err ⇒ Failure(JsResultException(err)),
      valid = value ⇒ Success(value)
    ))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}

case class FileHandler(file: File, promise: Promise[File]) extends FileAsyncHttpResponseHandler(file) {
  override def onSuccess(response: File) {
    promise.complete(Success(response))
  }
  override def onFailure(t: Throwable, response: String) {
    promise.complete(Failure(t))
  }
}

class FutureHttpClient {
  private val c = new AsyncHttpClient
  private def promised[A](f: Promise[A] ⇒ Unit) = {
    val promise = Promise[A]()
    f(promise); promise.future
  }
  def getJson[A: Reads](url: String, params: Map[String, String] = Map()) = {
    val promise = Promise[A]()
    c.get(url, new RequestParams(mapAsJavaMap(params)), JsonHandler(promise))
    promise.future
  }
  def getFile(file: File, url: String, params: Map[String, String] = Map()) = {
    promised[File](p ⇒ c.get(url, new RequestParams(mapAsJavaMap(params)), FileHandler(file, p)))
  }
  def get(url: String, params: Map[String, String] = Map()) = {
    promised[String](p ⇒ c.get(url, new RequestParams(mapAsJavaMap(params)), PlainHandler(p)))
  }
}