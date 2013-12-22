package net.routestory.display

import net.routestory.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.{ ScrollView, LinearLayout, TextView, ImageView }
import scala.concurrent._
import ExecutionContext.Implicits.global
import net.routestory.parts.{ FragmentData, Styles, RouteStoryFragment }
import net.routestory.parts.Styles._
import org.macroid.contrib.Layouts.{ HorizontalLinearLayout, VerticalLinearLayout }
import ViewGroup.LayoutParams._
import net.routestory.explore.PreviewRow
import scala.ref.WeakReference
import net.routestory.model.Story

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
        authorPicture ~> { x ⇒
          x.setScaleType(ImageView.ScaleType.FIT_START)
          x.setAdjustViewBounds(true)
        }
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
        PreviewRow.fillTags(tagRows, width - 20, t.map((_, None)), WeakReference(getActivity))
      } getOrElse {
        tagStuff ~> hide
      }
    }
  }
}
