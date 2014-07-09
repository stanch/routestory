package net.routestory.browsing

import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.{ ImageView, LinearLayout, ScrollView, TextView }
import macroid.FullDsl._
import macroid.Tweak
import macroid.contrib.ExtraTweaks._
import macroid.contrib.Layouts.VerticalLinearLayout
import macroid.util.Ui
import net.routestory.R
import net.routestory.data.Story
import net.routestory.viewable.StoryPreviewViewable
import net.routestory.ui.{ RouteStoryFragment, Styles }
import org.apmem.tools.layouts.FlowLayout

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class StoryDetailsFragment extends RouteStoryFragment {
  lazy val story: Future[Story] = null // TODO: !

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
