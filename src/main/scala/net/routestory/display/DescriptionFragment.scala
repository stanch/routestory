package net.routestory.display

import net.routestory.R
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.{ ScrollView, LinearLayout, TextView, ImageView }
import net.routestory.explore.ResultRow
import android.graphics.Bitmap
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.parts.StoryFragment
import net.routestory.parts.Implicits._
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._
import org.scaloid.common._

class DescriptionFragment extends StoryFragment {
  lazy val mStory = getActivity.asInstanceOf[HazStory].getStory

  var authorPicture: ImageView = _
  var authorName: TextView = _
  var description: TextView = _
  var tagStuff: LinearLayout = _
  var tagRows: LinearLayout = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    l[ScrollView](
      l[VerticalLinearLayout](
        w[TextView] ~> text("Author") ~> headerStyle(noPadding = true),
        w[ImageView] ~> lp(100 dip, WRAP_CONTENT) ~> wire(authorPicture),
        w[TextView] ~> lp(MATCH_PARENT, WRAP_CONTENT) ~> wire(authorName) ~> TextSize.medium,

        w[TextView] ~> text(R.string.description) ~> headerStyle(),
        w[TextView] ~> lp(MATCH_PARENT, WRAP_CONTENT) ~> wire(description) ~> TextSize.medium,

        l[VerticalLinearLayout](
          w[TextView] ~> text(R.string.tags) ~> headerStyle(),
          l[HorizontalLinearLayout](
            l[VerticalLinearLayout]() ~> wire(tagRows)
          )
        ) ~> wire(tagStuff)
      ) ~> padding(left = (8 dip))
    )
  }

  override def onStart() {
    super.onStart()

    mStory onSuccessUi {
      case story ⇒
        // get screen width
        val display = getActivity.getWindowManager.getDefaultDisplay
        val width = display.getWidth()

        Option(story.author) map { a ⇒
          authorPicture.setScaleType(ImageView.ScaleType.FIT_START)
          authorPicture.setAdjustViewBounds(true)
          authorName ~> text(a.name)
          a.pictureCache.get onSuccessUi {
            case picture ⇒ Option(picture).map(authorPicture.setImageBitmap(_)).getOrElse(authorPicture ~> hide)
          }
        } getOrElse {
          authorName ~> text("Me")
          authorPicture ~> hide
        }

        val d = Option(story.description).filter(_.length > 0).getOrElse("No description.")
        description ~> text(d)

        Option(story.tags).filter(_.length > 0) map { t ⇒
          ResultRow.fillTags(tagRows, width - 20, t, getActivity)
        } getOrElse {
          tagStuff ~> hide
        }
    }
  }
}