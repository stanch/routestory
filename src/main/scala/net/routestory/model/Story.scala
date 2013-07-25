package net.routestory.model

import java.io.File
import java.io.FileOutputStream
import net.routestory.parts.BitmapUtils
import org.apache.commons.io.IOUtils
import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.codehaus.jackson.annotate.JsonProperty
import org.ektorp.Attachment
import org.ektorp.CouchDbConnector
import org.ektorp.support.CouchDbDocument
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.util.Log
import scala.ref.WeakReference
import com.google.android.gms.maps.model.LatLng
import scala.collection.JavaConversions._
import scala.concurrent.{ ExecutionContext, Future }

@JsonIgnoreProperties(ignoreUnknown = true)
object Story {
  class TimedData {
    @JsonIgnore
    def bind(story: Story) {
      this.storyRef = new WeakReference[Story](story)
    }

    @JsonIgnore
    def getLocation: LatLng = storyRef.get.get.getLocation(timestamp)

    @JsonProperty("timestamp") var timestamp = 0
    @JsonIgnore protected var storyRef: WeakReference[Story] = _
  }

  class LocationData {
    @JsonIgnore
    def asLatLng: LatLng = new LatLng(coordinates(0), coordinates(1))

    @JsonProperty("type") protected val `type` = "Point"
    @JsonProperty("timestamp") var timestamp: Int = _
    @JsonProperty("coordinates") var coordinates: Array[Double] = _
  }

  object LocationData {
    def apply(time: Int, lat: Double, lng: Double) = new LocationData {
      timestamp = time
      coordinates = Array(lat, lng)
    }
  }

  class MediaData extends TimedData with Cache[File] {
    @JsonIgnore
    protected def retrieve(context: Context): File = {
      new File(String.format("%s/%s-%s.bin", context.getExternalCacheDir.getAbsolutePath, storyRef.get.get.getId, attachment_id.replace("/", "_")))
    }

    @JsonIgnore
    def isCached(context: Context): Boolean = retrieve(context).exists

    @JsonIgnore
    def cache(context: Context): Boolean = {
      val cache: File = retrieve(context)
      try {
        val input = storyRef.get.get.couchRef.get.get.getAttachment(storyRef.get.get.getId, attachment_id)
        cache.setReadable(true, false)
        val output = new FileOutputStream(cache)
        IOUtils.copy(input, output)
        output.close()
        Log.v("Story", "Cached media " + storyRef.get.get.getId + "/" + attachment_id)
        true
      } catch {
        case e: Throwable ⇒ e.printStackTrace(); false
      }
    }

    @JsonProperty("attachment_id") var attachment_id: String = _
  }

  class AudioData extends MediaData

  object AudioData {
    def apply(time: Int, aid: String) = new AudioData {
      timestamp = time
      attachment_id = aid
    }
  }

  class ImageData extends MediaData {
    @JsonIgnore
    def get(maxSize: Int)(implicit ctx: Context, ec: ExecutionContext): Future[Bitmap] = get(ctx, ec) map {
      file ⇒
        if (maxSize > 0) {
          BitmapUtils.decodeFile(file, maxSize)
        } else {
          BitmapFactory.decodeFile(file.getAbsolutePath)
        }
    }
  }

  object ImageData {
    def apply(time: Int, aid: String) = new ImageData {
      timestamp = time
      attachment_id = aid
    }
  }

  class TextData extends TimedData {
    @JsonProperty("text") var text: String = _
  }

  object TextData {
    def apply(time: Int, t: String) = new TextData {
      timestamp = time
      text = t
    }
  }

  class HeartbeatData extends TimedData {
    @JsonIgnore
    def getVibrationPattern(times: Int): Array[Long] = {
      val p_wave = 80L
      val t_wave = 100L
      val short_interval = 150L
      val beat_interval = 60 * 1000L / bpm
      val long_interval = Math.max(beat_interval - short_interval - p_wave - t_wave, 0)
      val pattern = List(p_wave, short_interval) ::: List.fill(99)(1L) ::: long_interval :: Nil
      (0L :: List.fill(Math.max(times, 1))(pattern).flatten).toArray
    }

    @JsonProperty("bpm") var bpm: Int = _
  }

  object HeartbeatData {
    def apply(time: Int, b: Int) = new HeartbeatData {
      timestamp = time
      bpm = b
    }
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Story extends CouchDbDocument with CouchDbObject {
  import Story._

  @JsonIgnore
  def bind(couch: CouchDbConnector) {
    couchRef = new WeakReference[CouchDbConnector](couch)
    List(audio, photos, notes, voice, heartbeat).foreach(_.foreach(_.bind(this)))
    if (audioPreview != null) {
      audioPreview.bind(this)
    }
  }

  @JsonIgnore
  def start() {
    starttime = System.currentTimeMillis / 1000L
    setId(s"story-${Shortuuid.uuid}")
    setRevision(null)
  }

  @JsonIgnore
  def end() {
    duration = (System.currentTimeMillis / 1000L - starttime).toInt
  }

  @JsonIgnore
  def addLocation(time: Long, l: Location): Story.LocationData = {
    val f = LocationData((time - starttime).toInt, l.getLatitude, l.getLongitude)
    locations.add(f)
    f
  }

  @JsonIgnore
  protected def getMedia[A <: MediaData](time: Long, id: String, contentType: String, init: (Int, String) ⇒ A): A = {
    addInlineAttachment(new Attachment(id, "stub", contentType))
    init((time - starttime).toInt, id)
  }

  @JsonIgnore
  def addAudio(time: Long, contentType: String, ext: String): String = {
    val id = s"audio/${audio.size + 1}.$ext"
    audio.add(getMedia(time, id, contentType, AudioData.apply))
    id
  }

  @JsonIgnore
  def addAudioPreview(contentType: String, ext: String): String = {
    val id = "audio/preview." + ext
    audioPreview = getMedia(0, id, contentType, AudioData.apply)
    id
  }

  @JsonIgnore
  def addVoice(time: Long, contentType: String, ext: String): String = {
    val id = s"voice/${voice.size + 1}.$ext"
    voice.add(getMedia(time, id, contentType, AudioData.apply))
    id
  }

  @JsonIgnore
  def addPhoto(time: Long, contentType: String, ext: String): String = {
    val id = s"images/${photos.size + 1}.$ext"
    photos.add(getMedia(time, id, contentType, ImageData.apply))
    id
  }

  @JsonIgnore
  def addNote(time: Long, note: String) {
    notes.add(TextData((time - starttime).toInt, note))
  }

  @JsonIgnore
  def addHeartbeat(time: Long, bpm: Int) {
    heartbeat.add(HeartbeatData((time - starttime).toInt, bpm))
  }

  @JsonIgnore
  def setTags(tags: String) {
    if (tags.trim.length > 0) {
      this.tags = tags.split(",").map(_.trim).filter(!_.isEmpty)
    }
  }

  @JsonIgnore
  def getLocation(time: Double): LatLng = {
    locations.toList.span(_.timestamp < time) match {
      case (Nil, Nil) ⇒ null
      case (Nil, l2 :: after) ⇒ l2.asLatLng
      case (before, Nil) ⇒ before.last.asLatLng
      case (before, l2 :: after) ⇒
        val l1 = before.last
        val t = (time - l1.timestamp) / (l2.timestamp - l1.timestamp)
        new LatLng(l1.coordinates(0) + t * (l2.coordinates(0) - l1.coordinates(0)), l1.coordinates(1) + t * (l2.coordinates(1) - l1.coordinates(1)))
    }
  }

  @JsonIgnore
  private var couchRef: WeakReference[CouchDbConnector] = _

  @JsonProperty("type") val `type` = "story"
  @JsonProperty("private") var isPrivate = false
  @JsonProperty("author") var authorId: String = _
  @JsonIgnore var author: Author = _

  @JsonProperty("starttime") var starttime = 0L
  @JsonProperty("duration") var duration = 0

  @JsonProperty("title") var title: String = null
  @JsonProperty("description") var description: String = null
  @JsonProperty("tags") var tags: Array[String] = null

  @JsonProperty("locations") var locations = new java.util.LinkedList[Story.LocationData]()
  @JsonProperty("audio") var audio = new java.util.LinkedList[Story.AudioData]()
  @JsonProperty("audio_preview") var audioPreview: Story.AudioData = null
  @JsonProperty("photos") var photos = new java.util.LinkedList[Story.ImageData]()
  @JsonProperty("notes") var notes = new java.util.LinkedList[Story.TextData]()
  @JsonProperty("voice") var voice = new java.util.LinkedList[Story.AudioData]()
  @JsonProperty("heartbeat") var heartbeat = new java.util.LinkedList[Story.HeartbeatData]()
}

