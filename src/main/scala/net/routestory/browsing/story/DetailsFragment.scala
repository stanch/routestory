package net.routestory.browsing.story

import android.os.Bundle
import android.view.ViewGroup.LayoutParams._
import android.view.{ LayoutInflater, View, ViewGroup }
import android.widget.{ ImageView, LinearLayout, ScrollView, TextView }
import macroid.FullDsl._
import macroid.Tweak
import macroid.contrib.{ TextTweaks, LpTweaks, ImageTweaks }
import macroid.contrib.Layouts.VerticalLinearLayout
import net.routestory.R
import net.routestory.data.Story
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.viewable.StoryPreviewListable
import org.apmem.tools.layouts.FlowLayout

import scala.concurrent.Future

class DetailsFragment extends RouteStoryFragment {
  lazy val story: Future[Story] = null // TODO: !

  var authorPicture = slot[ImageView]
  var authorName = slot[TextView]
  var description = slot[TextView]
  var tagStuff = slot[LinearLayout]
  var tags = slot[FlowLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = getUi {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] <~ text("Author") <~ Styles.header,
        w[ImageView] <~ lp[LinearLayout](100 dp, WRAP_CONTENT) <~ wire(authorPicture),
        w[TextView] <~ LpTweaks.matchWidth <~ wire(authorName) <~ TextTweaks.medium,

        w[TextView] <~ text(R.string.description) <~ Styles.header,
        w[TextView] <~ LpTweaks.matchWidth <~ wire(description) <~ TextTweaks.medium,

        l[VerticalLinearLayout](
          w[TextView] <~ text(R.string.tags) <~ Styles.header,
          l[FlowLayout]() <~ wire(tags)
        ) <~ wire(tagStuff)
      ) <~ padding(left = 8 dp)
    )
  }

  override def onStart() {
    super.onStart()

    story foreachUi { s ⇒
      val fillAuthor = s.author map { a ⇒
        (authorPicture <~ ImageTweaks.adjustBounds <~ Tweak[ImageView] { x ⇒
          x.setScaleType(ImageView.ScaleType.FIT_START)
        }) ~ (authorName <~ text(a.name))
      } getOrElse {
        (authorName <~ text("Me")) ~ (authorPicture <~ hide)
      }

      val d = s.meta.description.filter(!_.isEmpty).getOrElse("No description.")
      val fillDescription = description <~ text(d)

      val fillTags = Option(s.meta.tags).filter(_.nonEmpty) map { t ⇒
        tags <~ addViews(t.map(StoryPreviewListable.tag(_, None)), removeOld = true)
      } getOrElse {
        tagStuff <~ hide
      }

      runUi(fillAuthor, fillDescription, fillTags)
    }
  }
}
