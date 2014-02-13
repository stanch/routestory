package net.routestory.display

import scala.concurrent._

import android.os.Bundle
import android.view.{ LayoutInflater, View, ViewGroup }
import android.view.ViewGroup.LayoutParams._
import android.widget.{ ImageView, LinearLayout, ScrollView, TextView }

import ExecutionContext.Implicits.global
import org.macroid.FullDsl._
import org.macroid.contrib.ExtraTweaks._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }

import net.routestory.R
import net.routestory.model.Story
import net.routestory.ui.{ RouteStoryFragment, Styles }
import net.routestory.util.FragmentData
import net.routestory.disp.StoryPreviewViewable

class DetailsFragment extends RouteStoryFragment with FragmentData[Future[Story]] {
  lazy val story = getFragmentData

  var authorPicture = slot[ImageView]
  var authorName = slot[TextView]
  var description = slot[TextView]
  var tagStuff = slot[LinearLayout]
  var tagRows = slot[LinearLayout]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] ~> text("Author") ~> Styles.header(noPadding = true),
        w[ImageView] ~> lp(100 dp, WRAP_CONTENT) ~> wire(authorPicture),
        w[TextView] ~> lp(MATCH_PARENT, WRAP_CONTENT) ~> wire(authorName) ~> TextSize.medium,

        w[TextView] ~> text(R.string.description) ~> Styles.header(),
        w[TextView] ~> lp(MATCH_PARENT, WRAP_CONTENT) ~> wire(description) ~> TextSize.medium,

        l[VerticalLinearLayout](
          w[TextView] ~> text(R.string.tags) ~> Styles.header(),
          l[HorizontalLinearLayout](
            l[VerticalLinearLayout]() ~> wire(tagRows)
          )
        ) ~> wire(tagStuff)
      ) ~> padding(left = 8 dp)
    )
  }

  override def onStart() {
    super.onStart()

    story foreachUi { s ⇒
      // get screen width
      val width = displaySize(0)

      s.author map { a ⇒
        authorPicture ~> (tweak doing { x ⇒
          x.setScaleType(ImageView.ScaleType.FIT_START)
          x.setAdjustViewBounds(true)
        })
        authorName ~> text(a.name)
        //        a.pictureCache.get foreach {
        //          picture ⇒ Option(picture).map(b ⇒ authorPicture ~> (_.setImageBitmap(b))).getOrElse(authorPicture ~> hide)
        //        }
      } getOrElse {
        authorName ~> text("Me")
        authorPicture ~> hide
      }

      val d = s.meta.description.filter(!_.isEmpty).getOrElse("No description.")
      description ~> text(d)

      Option(s.meta.tags).filter(!_.isEmpty) map { t ⇒
        StoryPreviewViewable.fillTags(tagRows, width - 20, t.map((_, None)))
      } getOrElse {
        tagStuff ~> hide
      }
    }
  }
}
