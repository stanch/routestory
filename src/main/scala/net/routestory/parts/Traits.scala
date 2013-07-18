package net.routestory.parts

import org.scaloid.common._
import android.app.Fragment
import android.view.View
import net.routestory.StoryApplication
import android.app.Activity
import org.macroid.Concurrency
import android.content.Context
import scala.concurrent.{ Future, future, ExecutionContext }

trait FirstEveryStart {
    var everStarted = false
    def onFirstStart() {}
    def onEveryStart() {}
}

trait CacherSupport extends Concurrency {
    implicit def cacher2Future[A](c: Cacher[A])(implicit ctx: Context, ec: ExecutionContext): Future[A] = {
        if (c.isCached(ctx)) {
            Future.successful(c.get(ctx))
        } else future {
            c.cache(ctx)
            c.get(ctx)
        }
    }
    implicit def cacher2RichFuture[A](c: Cacher[A])(implicit ctx: Context, ec: ExecutionContext): RichFuture[A] = cacher2Future(c)
}

trait StoryActivity extends Activity with Concurrency with CacherSupport with FirstEveryStart with SActivity {
    lazy val app = getApplication.asInstanceOf[StoryApplication]
    lazy val bar = getActionBar

    def findView[A <: View](id: Int) = findViewById(id).asInstanceOf[A]
    def findFrag[A <: Fragment](tag: String) = getFragmentManager.findFragmentByTag(tag).asInstanceOf[A]

    override def onStart() {
        super.onStart()
        if (!everStarted) {
            onFirstStart()
            everStarted = true
        }
        onEveryStart()
    }
}

trait StoryFragment extends Fragment with FirstEveryStart with Concurrency with CacherSupport {
    lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]
    implicit lazy val ctx = getActivity

    def findView[A <: View](id: Int) = getView.findViewById(id).asInstanceOf[A]
    def findFrag[A <: Fragment](tag: String) = getChildFragmentManager.findFragmentByTag(tag).asInstanceOf[A]

    override def onStart() {
        super.onStart()
        if (!everStarted) {
            onFirstStart()
            everStarted = true
        }
        onEveryStart()
    }
}