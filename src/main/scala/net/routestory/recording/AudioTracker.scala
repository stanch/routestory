package net.routestory.recording

import android.os.{ Message, Handler }
import android.media.{ AudioFormat, AudioRecord }
import AudioFormat._
import java.io.{ FileOutputStream, File }
import scala.ref.WeakReference
import android.content.Context
import org.macroid.util.Map2Bundle
import android.util.Log
import com.todoroo.aacenc.AACEncoder
import net.routestory.StoryApplication
import org.apache.commons.io.FileUtils
import java.nio.{ ByteOrder, ByteBuffer }

class AudioTracker(ctx: WeakReference[Context], handler: Handler, var piecesDone: Int = 0) extends Runnable {
  import AudioTracker._

  val frameSize = ms(10) * 2 // 10 ms * 2B per Float

  val buffer = new Array[Byte](bufferSize)

  override def run() {
    var working = true
    while (working) {
      /* setup recording */
      Log.d("AudioTracker", "Starting")
      val audioRecord = new AudioRecord(
        1, 44100, CHANNEL_IN_MONO, ENCODING_PCM_16BIT,
        AudioRecord.getMinBufferSize(44100, CHANNEL_IN_MONO, ENCODING_PCM_16BIT) * 10
      )
      audioRecord.startRecording()
      val dump = File.createTempFile("audio-sample", ".snd", ctx.get.get.getExternalCacheDir)
      val dumpStream = new FileOutputStream(dump)

      /* record frames */
      Log.d("AudioTracker", "Recording")
      var offset = 0
      while (offset < bufferSize - frameSize) {
        offset += audioRecord.read(buffer, offset, frameSize)
        if (Thread.interrupted()) {
          Log.d("AudioTracker", "Interrupted")
          working = false
          offset = bufferSize
        }
      }
      Log.d("AudioTracker", "Stopping")
      audioRecord.stop()
      if (working) {
        dumpStream.write(buffer)

        /* clean up and send to the activity */
        Log.d("AudioTracker", "Saving")
        dumpStream.close()
        val msg = new Message
        msg.setData(Map2Bundle(Map("path" → dump.getAbsolutePath)))
        handler.sendMessage(msg)
        piecesDone += 1

        /* sleep tight */
        Log.d("AudioTracker", "Entering sleep")
        try {
          Thread.sleep(delay(piecesDone))
        } catch {
          case _: InterruptedException ⇒ working = false
        }
        if (Thread.interrupted()) working = false
        if (!working) Log.d("AudioTracker", "Interrupted")
      }
    }
  }
}

object AudioTracker {
  def ms(v: Int) = (44.100 * v).toInt

  val bufferSize = ms(10000) * 2 // 10s * 2B per float
  def delay(n: Int) = 50e3.toInt * Math.pow(2, n / 5).toInt // 50s, doubles every 5 pieces
  val fadeLength = ms(1500)

  def process(ctx: Context, pieces: List[(Long, String)]): (Option[String], List[(Long, String)]) = {
    /* remove stuff that we don't need, as in
     * ....  .  .  .  .  .    .    .    .    .    .         .         .
     *  xxx     x  x     x         x         x      ← dst →           ↑ start here
     */
    val dst = delay(pieces.length) * 5 / 8
    // note that `pieces` is reversed, i.e. starts with the latest one
    val sparse = pieces.foldLeft(List[(Long, String)]()) {
      case (Nil, p) ⇒ p :: Nil
      case (acc, (time, path)) if acc.head._1 - time > dst / 1000 ⇒ (time, path) :: acc
      case (acc, _) ⇒ acc
    }

    Log.d("AudioTracker", pieces.reverse.toString())
    Log.d("AudioTracker", sparse.toString())

    //
    val encoder = new AACEncoder
    val dur = ms(StoryApplication.storyPreviewDuration * 1000)
    val preview = new Array[Short](dur)
    val previewBytes = new Array[Byte](dur * 2)
    val offset = if (sparse.length > 1) {
      (dur - bufferSize) / (sparse.length - 1)
    } else 0

    var (i, j) = (0, 0)
    var index = 0
    val sounds = sparse map { piece ⇒
      /* read piece */
      val pcmFile = new File(piece._2)
      val data = FileUtils.readFileToByteArray(pcmFile)
      pcmFile.delete()

      /* create fade-ins and fade-outs */
      val faded = new Array[Short](data.length / 2)
      val shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(faded)
      i = 0
      while (i < fadeLength) {
        val factor = Math.pow(i.toDouble / fadeLength, 2)
        faded(i) = (faded(i) * factor).toShort
        faded(faded.length - 1 - i) = (faded(faded.length - 1 - i) * factor).toShort
        i += 1
      }
      shortBuffer.clear()
      shortBuffer.put(faded)

      /* add to preview */
      i = 0
      j = index
      while (i < faded.length && j < dur) {
        // see http://www.vttoth.com/CMS/index.php/technical-notes/68
        preview(j) = (faded(i) + preview(j) - ((faded(i).toLong * preview(j)) >>> 16)).toShort
        i += 1
        j += 1
      }
      index += offset

      /* encode to aac */
      val aacFile = new File(piece._2 + ".aac")
      encoder.init(64000, 1, 44100, 16, aacFile.getAbsolutePath)
      encoder.encode(data)
      encoder.uninit()
      (piece._1, aacFile.getAbsolutePath)
    }

    /* encode preview */
    val glimpse = if (sparse.length > 1) {
      val previewAacFile = File.createTempFile("preview", ".aac", ctx.getExternalCacheDir)
      ByteBuffer.wrap(previewBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(preview)
      encoder.init(64000, 1, 44100, 16, previewAacFile.getAbsolutePath)
      encoder.encode(previewBytes)
      encoder.uninit()
      Some(previewAacFile.getAbsolutePath)
    } else None

    (glimpse, sounds)
  }
}