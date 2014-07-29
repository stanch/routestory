package net.routestory.recording.logged

import akka.actor.{ ActorLogging, Props, FSM, Actor }
import net.routestory.data.Story
import scala.concurrent._
import android.media.AudioRecord
import android.media.AudioFormat._
import java.io.{ FileOutputStream, File }
import org.apache.commons.io.FileUtils
import java.nio.{ ByteOrder, ByteBuffer }
import com.todoroo.aacenc.AACEncoder
import macroid.AppContext
import scala.concurrent.duration._
import net.routestory.recording.Typewriter

object Dictaphone {
  sealed trait State
  case object Off extends State
  case object Idle extends State
  case object Recording extends State

  sealed trait Data
  case object NoData extends Data
  case class RecordingData(ar: AudioRecord, offset: Int) extends Data

  def ms(v: Int) = (44.100 * v).toInt
  val gapDuration = 50.seconds
  val fadeLength = ms(1500) // 1.5s
  val pieceLength = ms(10000) // 10s
  val frameSize = ms(10) * 2 // 10 ms * 2B per Float
  val bufferSize = pieceLength * 2 // * 2B per Float
  val buffer = new Array[Byte](bufferSize)

  case object SwitchOn
  case object SwitchOff
  case object SwitchedOff
  case object ReadFrame

  def processPiece(filename: String)(implicit ec: ExecutionContext) = future {
    val pcmFile = new File(filename)
    val data = FileUtils.readFileToByteArray(pcmFile)
    pcmFile.delete()

    /* create fade-ins and fade-outs */
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
    aacFile
  }

  def props(implicit ctx: AppContext) = Props(new Dictaphone).withDispatcher("dictaphone-pinned-dispatcher")
}

class Dictaphone(implicit ctx: AppContext) extends Actor with FSM[Dictaphone.State, Dictaphone.Data] with ActorLogging {
  import Dictaphone._
  import context.dispatcher

  lazy val typewriter = context.actorSelection("../typewriter")

  startWith(Idle, NoData)

  when(Off) {
    case Event(SwitchOn, _) ⇒ goto(Idle)
    case Event(SwitchOff, _) ⇒
      sender ! SwitchedOff; stay()
    case _ ⇒ stay()
  }

  when(Idle, stateTimeout = gapDuration) {
    case Event(SwitchOff, _) ⇒
      sender ! SwitchedOff
      goto(Off)
    case Event(StateTimeout, _) ⇒
      log.debug("Start recording")
      val audioRecord = new AudioRecord(
        1, 44100, CHANNEL_IN_MONO, ENCODING_PCM_16BIT,
        AudioRecord.getMinBufferSize(44100, CHANNEL_IN_MONO, ENCODING_PCM_16BIT) * 10
      )
      audioRecord.startRecording()
      self ! ReadFrame
      goto(Recording) using RecordingData(audioRecord, 0)
    case _ ⇒
      stay()
  }

  when(Recording) {
    case Event(ReadFrame, RecordingData(ar, offset)) if offset < bufferSize - frameSize ⇒
      val off = offset + ar.read(buffer, offset, frameSize)
      self ! ReadFrame
      stay() using RecordingData(ar, off)
    case Event(ReadFrame, RecordingData(ar, _)) ⇒
      ar.stop()
      log.debug("Saving record")
      val dump = File.createTempFile("audio-sample", ".snd", ctx.get.getExternalCacheDir)
      val dumpStream = new FileOutputStream(dump)
      dumpStream.write(buffer)
      dumpStream.close()
      // pipeTo, y u no work with ActorSelection?
      processPiece(dump.getAbsolutePath).map(Story.Sound.apply) foreach { s ⇒ typewriter ! Typewriter.Element(s) }
      goto(Idle) using NoData
    case Event(SwitchOff, RecordingData(ar, _)) ⇒
      ar.stop()
      sender ! SwitchedOff
      goto(Off) using NoData
  }

  initialize()
}
