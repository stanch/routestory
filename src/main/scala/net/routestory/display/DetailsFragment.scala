package net.routestory.display

import net.routestory.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.{ ScrollView, LinearLayout, TextView, ImageView }
import net.routestory.explore.ResultRow
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.parts.{ Styles, RouteStoryFragment }
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._

class DetailsFragment extends RouteStoryFragment {
  lazy val story = getActivity.asInstanceOf[HazStory].story

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
      val display = getActivity.getWindowManager.getDefaultDisplay
      val width = display.getWidth()

      Option(s.author) map { a ⇒
        authorPicture ~> { x ⇒
          x.setScaleType(ImageView.ScaleType.FIT_START)
          x.setAdjustViewBounds(true)
        }
        authorName ~> text(a.name)
        a.pictureCache.get foreach {
          picture ⇒ Option(picture).map(b ⇒ authorPicture ~> (_.setImageBitmap(b))).getOrElse(authorPicture ~> hide)
        }
      } getOrElse {
        authorName ~> text("Me")
        authorPicture ~> hide
      }

      val d = Option(s.description).filter(_.length > 0).getOrElse("No description.")
      description ~> text(d)

      Option(s.tags).filter(_.length > 0) map { t ⇒
        ResultRow.fillTags(tagRows, width - 20, t.map((_, None)), getActivity)
      } getOrElse {
        tagStuff ~> hide
      }
    }
  }
}