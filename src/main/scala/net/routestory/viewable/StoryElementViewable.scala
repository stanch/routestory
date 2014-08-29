package net.routestory.viewable

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.view.{ View, Gravity }
import android.webkit.{ WebViewClient, WebView }
import android.widget._
import macroid.FullDsl._
import macroid.contrib.Layouts.{ VerticalLinearLayout, HorizontalLinearLayout }
import macroid.{ Ui, AppContext, ActivityContext, Tweak }
import macroid.contrib._
import macroid.viewable.Viewable
import net.routestory.data.Story
import uk.co.senab.photoview.PhotoViewAttacher
import net.routestory.util.BitmapPool.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global
import net.routestory.R

class StoryElementViewable(maxImageSize: Int) {
  def imageViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable[Story.Image] { x ⇒
    val bitmapTweak = x.data.map(_.bitmapTweak(maxImageSize) + Tweak[ImageView] { v ⇒
      new PhotoViewAttacher(v).update()
    })
    w[ImageView] <~ ImageTweaks.adjustBounds <~ bitmapTweak <~ LpTweaks.matchParent
  }

  def textNoteViewable(implicit ctx: ActivityContext, appCtx: AppContext) = Viewable.text {
    TextTweaks.size(30) + TextTweaks.color(Color.WHITE) + padding(all = 20 dp) + LpTweaks.matchParent +
      Tweak[TextView] { v ⇒
        v.setGravity(Gravity.CENTER)
      }
  }.contraMap[Story.TextNote](_.text)

  def audioViewable(implicit ctx: ActivityContext, appCtx: AppContext): Viewable[Story.Audio, LinearLayout] = Viewable[Story.Audio] { x ⇒
    var play = slot[Button]
    var pause = slot[Button]
    var seekBar = slot[SeekBar]

    val mediaPlayer = x.data.map { file ⇒
      new MediaPlayer {
        setDataSource(file.getAbsolutePath)
        prepare()
      }
    }

    val layout = l[HorizontalLinearLayout](
      w[Button] <~
        BgTweaks.res(R.drawable.play) <~
        lp[LinearLayout](32 dp, 32 dp) <~
        wire(play) <~
        On.click {
          (play <~ hide) ~
            (pause <~ show) ~
            Ui(mediaPlayer.foreach(_.start()))
        },
      w[Button] <~
        BgTweaks.res(R.drawable.pause) <~
        lp[LinearLayout](32 dp, 32 dp) <~
        wire(pause) <~
        hide <~
        On.click {
          (pause <~ hide) ~
            (play <~ show) ~
            Ui(mediaPlayer.foreach(_.pause()))
        },
      w[SeekBar] <~
        wire(seekBar) <~
        LpTweaks.matchWidth
    )

    layout <~ LpTweaks.matchParent <~
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
