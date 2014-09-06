package net.routestory.viewable

import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaPlayer.{ OnCompletionListener, OnSeekCompleteListener }
import android.os.Handler
import android.view.{ View, Gravity }
import android.webkit.{ WebViewClient, WebView }
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import macroid.{ Ui, AppContext, ActivityContext, Tweak }
import macroid.contrib._
import macroid.viewable.Viewable
import net.routestory.data.Story
import net.routestory.ui.Tweaks
import uk.co.senab.photoview.PhotoView
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.R
import net.routestory.util.Implicits._
import android.view.ViewGroup.LayoutParams._

object StoryElementViewable {
  def imageViewable(implicit ctx: ActivityContext, appCtx: AppContext): Viewable[Story.Image, LinearLayout] = Viewable[Story.Image] { x ⇒
    l[VerticalLinearLayout](
      w[PhotoView] <~ x.data.map(Tweaks.picasso) <~
        ImageTweaks.adjustBounds <~
        lp[LinearLayout](MATCH_PARENT, MATCH_PARENT, 1.0f),
      w[TextView] <~ x.caption.map(text).getOrElse(hide) <~
        TextTweaks.color(Color.WHITE) <~
        TextTweaks.medium <~
        padding(all = 4 dp)
    )
  }

  def textNoteViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable.text {
    TextTweaks.size(30) + TextTweaks.color(Color.WHITE) + padding(all = 20 dp) + LpTweaks.matchParent +
      Tweak[TextView] { v ⇒
        v.setGravity(Gravity.CENTER)
      }
  }.contraMap[Story.TextNote](_.text)

  def audioViewable(implicit ctx: ActivityContext, appCtx: AppContext): Viewable[Story.Audio, LinearLayout] = Viewable[Story.Audio] { x ⇒
    var playButton = slot[Button]
    var pauseButton = slot[Button]
    var seekBar = slot[SeekBar]

    val handler = new Handler
    val mediaPlayer = x.data.map { file ⇒
      new MediaPlayer {
        setDataSource(file.getAbsolutePath)
        prepare()
        setOnCompletionListener(new OnCompletionListener {
          def onCompletion(mp: MediaPlayer) = runUi(
            pauseButton <~ hide,
            playButton <~ show
          )
        })
      }
    }

    def seek: Ui[Any] = Ui {
      mediaPlayer foreachUi { mp ⇒
        runUi(seekBar <~ SeekTweaks.seek(mp.getCurrentPosition))
        if (mp.isPlaying) handler.postDelayed(seek, 100)
      }
    }

    val play = w[Button] <~
      BgTweaks.res(R.drawable.play) <~
      lp[LinearLayout](32 dp, 32 dp) <~
      wire(playButton) <~
      On.click {
        (playButton <~ hide) ~
          (pauseButton <~ show) ~
          Ui(mediaPlayer.foreach(_.start())) ~
          seek
      }

    val pause = w[Button] <~
      BgTweaks.res(R.drawable.pause) <~
      lp[LinearLayout](32 dp, 32 dp) <~
      wire(pauseButton) <~
      hide <~
      On.click {
        (pauseButton <~ hide) ~
          (playButton <~ show) ~
          Ui(mediaPlayer.foreach(_.pause()))
      }

    val bar = w[SeekBar] <~
      wire(seekBar) <~
      LpTweaks.matchWidth <~
      mediaPlayer.map(mp ⇒ Tweak[SeekBar](_.setMax(mp.getDuration))) <~
      Tweak[SeekBar] { x ⇒
        x.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
          def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = {
            if (fromUser) {
              mediaPlayer.foreach(_.seekTo(progress))
            }
          }
          def onStopTrackingTouch(seekBar: SeekBar) = ()
          def onStartTrackingTouch(seekBar: SeekBar) = ()
        })
      }

    val controls = l[HorizontalLinearLayout](play, pause, bar)

    val caption = w[TextView] <~
      TextTweaks.color(Color.WHITE) <~
      TextTweaks.large <~
      TextTweaks.italic <~
      padding(left = 4 dp, bottom = 8 dp) <~
      text(x match {
        case _: Story.VoiceNote ⇒ "Voice note"
        case _: Story.Sound ⇒ "Ambient sound"
      })

    l[VerticalLinearLayout](caption, controls) <~
      LpTweaks.matchParent <~ padding(all = 8 dp) <~
      Tweak[LinearLayout](_.setGravity(Gravity.CENTER))
  }

  def foursquareVenueViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.FoursquareVenue] { x ⇒
    w[WebView] <~
      LpTweaks.matchParent <~
      Tweak[WebView] { v ⇒
        v.setWebViewClient(new WebViewClient {
          override def shouldOverrideUrlLoading(view: WebView, url: String) = {
            view.loadUrl(url)
            true
          }
        })
        v.getSettings.setJavaScriptEnabled(true)
        v.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY)
        v.loadUrl(s"https://foursquare.com/v/${x.id}")
      }
  }

  implicit def storyElementViewable(implicit ctx: ActivityContext, appCtx: AppContext) =
    (imageViewable.toParent[Story.KnownElement] orElse
      textNoteViewable.toParent[Story.KnownElement] orElse
      audioViewable.toParent[Story.KnownElement] orElse
      foursquareVenueViewable.toParent[Story.KnownElement]).toTotal
}
