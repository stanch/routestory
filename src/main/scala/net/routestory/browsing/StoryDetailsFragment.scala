package net.routestory.browsing

import scala.concurrent._

import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, ScrollView, TextView }

import ExecutionContext.Implicits.global
import macroid.Tweak
import macroid.FullDsl._
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }

import net.routestory.R
import net.routestory.model.Story
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.util.FragmentData
import net.routestory.display.StoryPreviewViewable
import org.apmem.tools.layouts.FlowLayout
import macroid.util.Ui

class StoryDetailsFragment extends RouteStoryFragment with FragmentData[Future[Story]] {
  lazy val story = getFragmentData

  var authorPicture = slot[ImageView]
  var authorName = slot[TextView]
  var description = slot[TextView]
  var tagStuff = slot[LinearLayout]
  var tags = slot[FlowLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] <~ text("Author") <~ Styles.header(noPadding = true),
        w[ImageView] <~ lp[LinearLayout](100 dp, WRAP_CONTENT) <~ wire(authorPicture),
        w[TextView] <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT) <~ wire(authorName) <~ TextSize.medium,

        w[TextView] <~ text(R.string.description) <~ Styles.header(),
        w[TextView] <~ lp[LinearLayout](MATCH_PARENT, WRAP_CONTENT) <~ wire(description) <~ TextSize.medium,

        l[VerticalLinearLayout](
          w[TextView] <~ text(R.string.tags) <~ Styles.header(),
          l[FlowLayout]() <~ wire(tags)
        ) <~ wire(tagStuff)
      ) <~ padding(left = 8 dp)
    )
  }

  override def onStart() {
    super.onStart()

    story foreachUi { s ⇒
      // get screen width
      val width = displaySize(0)

      val fillAuthor = s.author map { a ⇒
        Ui.sequence(
          authorPicture <~ Tweak[ImageView] { x ⇒
            x.setScaleType(ImageView.ScaleType.FIT_START)
            x.setAdjustViewBounds(true)
          },
          authorName <~ text(a.name)
        )
      } getOrElse {
        Ui.sequence(
          authorName <~ text("Me"),
          authorPicture <~ hide
        )
      }

      val d = s.meta.description.filter(!_.isEmpty).getOrElse("No description.")
      val fillDescription = description <~ text(d)

      val fillTags = Option(s.meta.tags).filter(!_.isEmpty) map { t ⇒
        tags <~ addViews(t.map(StoryPreviewViewable.tag(_, None)), removeOld = true)
      } getOrElse {
        tagStuff <~ hide
      }

      runUi(fillAuthor, fillDescription, fillTags)
    }
  }
}
