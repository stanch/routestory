package net.routestory.recording

import android.os.{ Message, Handler }
import android.media.{ AudioFormat, AudioRecord }
import AudioFormat._
import java.io.{ FileOutputStream, File }
import scala.ref.WeakReference
import android.content.Context
import android.util.Log
import com.todoroo.aacenc.AACEncoder
import org.apache.commons.io.FileUtils
import java.nio.{ ByteOrder, ByteBuffer }
import scala.concurrent._
import java.util.concurrent.Executors
import org.macroid.Bundles

class AudioTracker(ctx: WeakReference[Context], handler: Handler, var piecesDone: Int = 0) extends Runnable with Bundles {
  import AudioTracker._

  val frameSize = ms(10) * 2 // 10 ms * 2B per Float
  val bufferSize = pieceLength * 2 // * 2B per Float
  val buffer = new Array[Byte](bufferSize)

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

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

        /* clean up, process and send to the activity */
        Log.d("AudioTracker", "Saving")
        dumpStream.close()
        future {
          val now = System.currentTimeMillis / 1000
          val processed = processPiece(dump.getAbsolutePath)
          val msg = new Message
          msg.setData(bundle("ts" → now, "path" → processed))
          handler.sendMessage(msg)
        }
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

  val pieceLength = ms(10000) // 10s
  val fadeLength = ms(1500) // 1.5s

  def delay(n: Int) = 50000 * Math.pow(2, n / 5).toInt // 50s, doubles every 5 pieces

  def processPiece(filename: String): String = {
    val pcmFile = new File(filename)
    val data = FileUtils.readFileToByteArray(pcmFile)
    pcmFile.delete()

    /* create fade-ins and fade-outs */
    Log.d("AudioTracker", "Creating fades")
    val faded = new Array[Short](data.length / 2)
    val shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(faded)
    var i = 0
    while (i < fadeLength) {
      val factor = Math.pow(i.toDouble / fadeLength, 2)
      faded(i) = (faded(i) * factor).toShort
      faded(faded.length - 1 - i) = (faded(faded.length - 1 - i) * factor).toShort
      i += 1
    }
    shortBuffer.clear()
    shortBuffer.put(faded)

    /* encode to aac */
    val aacFile = new File(filename + ".aac")
    new AACEncoder {
      init(64000, 1, 44100, 16, aacFile.getAbsolutePath)
      encode(data)
      uninit()
    }
    aacFile.getAbsolutePath
  }

  def sift(pieces: List[(Long, String)]) = {
    val dst = delay(pieces.length) * 5 / 8
    val (use, del) = sparse(pieces, dst)
    del.foreach(new File(_).delete())
    use
  }

  def sparse[A](data: List[(Long, A)], dst: Int): (List[(Long, A)], List[A]) = {
    /* remove stuff that we don't need, as in
     * ....  .  .  .  .  .    .    .    .    .    .         .         .
     *  xxx     x  x     x         x         x      ← dst →           ↑ start here
     */

    // note that `data` is reversed, i.e. starts with the latest one
    // also note that dst is in ms, while timestamps are in seconds
    data.foldLeft((List[(Long, A)](), List[A]())) {
      // always keep the first (i.e. last) piece
      case ((Nil, unused), p) ⇒ (p :: Nil, unused)
      // if the piece is far enough from the previous kept one, add to acc
      case ((acc, unused), p @ (time, _)) if acc.head._1 - time > dst / 1000 ⇒ (p :: acc, unused)
      // otherwise add to unused
      case ((acc, unused), (_, d)) ⇒ (acc, d :: unused)
    }
  }
}