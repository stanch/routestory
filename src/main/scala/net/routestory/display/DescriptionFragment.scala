package net.routestory.display

import net.routestory.R
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import net.routestory.model.Author
import net.routestory.model.Story
import net.routestory.explore.ResultRow
import android.graphics.Bitmap
import android.widget.ImageView
import android.content.Context
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Success,Failure}
import net.routestory.parts.StoryFragment
import net.routestory.parts.Implicits._

class DescriptionFragment extends StoryFragment {
    lazy val mStory = getActivity.asInstanceOf[HazStory].getStory
    
    override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
		inflater.inflate(R.layout.fragment_description, container, false)
	}
    
    def setAvatar(imageView: ImageView, bitmap: Bitmap) {
		if (bitmap != null) {
			imageView.setImageBitmap(bitmap)
		}
		else {
			imageView.setImageResource(R.drawable.ic_launcher) // set default image
		}
	}
    
    override def onStart() {
        super.onStart()
        
    	mStory onSuccessUi { case story =>
	    	// get screen width
			val display = getActivity.getWindowManager.getDefaultDisplay
			val width = display.getWidth()
			
			if (story.author != null) {
			    val avatar = findView[ImageView](R.id.authorPicture)
			    avatar.setScaleType(ImageView.ScaleType.FIT_START)
				avatar.setAdjustViewBounds(true)
			    findView[TextView](R.id.authorName).setText(story.author.name)
			    story.author.getPicture onSuccessUi { case picture =>
			        setAvatar(avatar, picture)
			    }
			} else {
			    findView[TextView](R.id.authorName).setText("Me") // TODO: strings.xml!
			}
			
			if (story.description != null && story.description.length() > 0) {
			    findView[TextView](R.id.storyDescription).setText(story.description)
			} else {
			    findView[TextView](R.id.storyDescription).setText("No description.") // TODO: strings.xml!
			}
			
			if (story.tags != null && story.tags.length > 0) {
				ResultRow.fillTags(findView[LinearLayout](R.id.storyTagRows), width-20, story.tags, getActivity)
			} else {
			    findView[View](R.id.storyTagsHeader).setVisibility(View.GONE)
			    findView[View](R.id.storyTags).setVisibility(View.GONE)
			}
    	}
    }
}