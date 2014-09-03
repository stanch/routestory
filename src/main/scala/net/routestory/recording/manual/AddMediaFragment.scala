package net.routestory.recording.manual

import java.io.File

import akka.pattern.ask
import akka.util.Timeout
import android.content.{ DialogInterface, Intent }
import android.media.MediaRecorder
import android.net.Uri
import android.os.{ Bundle, Environment }
import android.provider.MediaStore
import android.support.v4.app.DialogFragment
import android.view.{ Gravity, LayoutInflater, ViewGroup }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import macroid.contrib.{ ImageTweaks, LpTweaks, TextTweaks }
import macroid.viewable.Listable
import macroid.{ IdGeneration, Transformer, Tweak, Ui }
import net.routestory.R
import net.routestory.data.Story
import net.routestory.recording.logged.Dictaphone
import net.routestory.recording.{ RecordFragment, Typewriter }
import net.routestory.ui.RouteStoryFragment

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddMediaFragment extends RouteStoryFragment with IdGeneration with RecordFragment {
  def photoFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RouteStory")
    root.mkdirs()
    File.createTempFile("photo", ".jpg", root)
  }

  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = getUi {
    def clicker(factory: Ui[DialogFragment], tag: String) =
      factory.map(_.show(getChildFragmentManager, tag))

    val cameraClicker = Ui {
      activity.lastPhotoFile = Some(photoFile)
      val intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
      intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(activity.lastPhotoFile.get))
      getActivity.startActivityForResult(intent, activity.requestCodePhoto)
    }

    val buttons = Seq(
      (R.drawable.ic_action_camera, "Photo", cameraClicker),
      (R.drawable.ic_action_view_as_list, "Text note", clicker(f[AddTextNote].factory, Tag.noteDialog)),
      (R.drawable.ic_action_mic, "Voice note", clicker(f[AddVoiceNote].factory, Tag.voiceDialog))
    )

    val listable = Listable[(Int, String, Ui[Unit])].tr {
      l[HorizontalLinearLayout](
        w[ImageView],
        w[TextView] <~ TextTweaks.large
      ) <~ padding(top = 12 dp, bottom = 12 dp, left = 8 dp)
    }(button ⇒ Transformer {
      case img: ImageView ⇒ img <~ ImageTweaks.res(button._1)
      case txt: TextView ⇒ txt <~ text(button._2)
      case l @ Transformer.Layout(_*) ⇒ l <~ On.click(button._3)
    })

    w[ListView] <~ listable.listAdapterTweak(buttons)
  }
}

class AddSomething extends DialogFragment with RouteStoryFragment with RecordFragment {
  lazy val typewriter = actorSystem.map(_.actorSelection("/user/typewriter"))
}

class AddTextNote extends AddSomething {
  var input = slot[EditText]

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[EditText] <~ Tweak[EditText] { x ⇒
      x.setHint(R.string.message_typenotehere)
      x.setMinLines(5)
      x.setGravity(Gravity.TOP)
    } <~ wire(input)
  } <~ positiveOk(Ui {
    input.map(_.getText.toString).filter(_.nonEmpty).foreach { text ⇒
      typewriter.foreach(_ ! Typewriter.Element(Story.TextNote(text)))
    }
  }) <~ negativeCancel(Ui.nop)).create()
}

class AddPhotoCaption extends AddSomething {
  var input = slot[EditText]

  lazy val photoFile = new File(getArguments.getString("photoFile"))

  override def onCancel(dialog: DialogInterface) = {
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(None, photoFile)))
    super.onCancel(dialog)
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    w[EditText] <~ Tweak[EditText] { x ⇒
      x.setHint("Type a caption here")
      x.setMinLines(5)
      x.setGravity(Gravity.TOP)
    } <~ wire(input)
  } <~ positiveOk(Ui {
    val cap = input.map(_.getText.toString).filter(_.nonEmpty)
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(cap, photoFile)))
  }) <~ negative("No caption")(Ui {
    typewriter.foreach(_ ! Typewriter.Element(Story.Photo(None, photoFile)))
  })).create()
}

class AddEasiness extends AddSomething {
  setCancelable(false)
  var rating = slot[RatingBar]

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    l[VerticalLinearLayout](
      w[TextView] <~ text("How easy was it?") <~
        TextTweaks.large <~ padding(all = 4 dp),
      w[RatingBar] <~ wire(rating) <~
        Tweak[RatingBar](_.setNumStars(5)) <~
        LpTweaks.wrapContent
    )
  } <~ positiveOk {
    Ui(typewriter.map(_ ! Typewriter.Easiness(rating.get.getRating))) ~~ activity.save
  }).create()
}

class AddVoiceNote extends AddSomething {
  implicit val dictaphoneSwitchOffTimeout = Timeout(2000)
  lazy val dictaphone = actorSystem.map(_.actorSelection("/user/dictaphone"))

  var mediaRecorder: Option[Future[MediaRecorder]] = None

  lazy val voiceNoteFile = {
    val root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "RouteStory")
    root.mkdirs()
    File.createTempFile("voice-note", ".mp4", root)
  }

  def stop = Ui {
    dictaphone.foreach(_ ! Dictaphone.SwitchOn)
    mediaRecorder.foreach(_.foreachUi { m ⇒
      m.stop()
      m.reset()
      m.release()
      mediaRecorder = None
    })
  }

  override def onCancel(dialog: DialogInterface) = {
    stop.run
    super.onCancel(dialog)
  }

  override def onStart() = {
    super.onStart()
    mediaRecorder = Some {
      dictaphone.flatMap(_ ? Dictaphone.SwitchOff)
        .mapUi(_ ⇒ new MediaRecorder {
          setAudioSource(AudioSource.MIC)
          setOutputFormat(OutputFormat.MPEG_4)
          setAudioEncoder(AudioEncoder.AAC)
          setOutputFile(voiceNoteFile.getAbsolutePath)
          prepare()
          start()
        })
    }
  }

  override def onCreateDialog(savedInstanceState: Bundle) = getUi(dialog {
    l[HorizontalLinearLayout](
      w[ImageView] <~ ImageTweaks.res(R.drawable.ic_action_mic),
      w[TextView] <~ TextTweaks.large <~ text("Recording...")
    ) <~ LpTweaks.matchParent <~
      Tweak[LinearLayout](_.setGravity(Gravity.CENTER)) <~
      padding(top = 12 dp)
  } <~ positive("Finish")(stop ~ Ui {
    typewriter.foreach(_ ! Typewriter.Element(Story.VoiceNote(voiceNoteFile)))
  }) <~ negativeCancel(stop)).create()
}
