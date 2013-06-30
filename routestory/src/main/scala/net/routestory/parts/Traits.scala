package net.routestory.parts

import scala.concurrent._
import ExecutionContext.Implicits.global
import org.scaloid.common._
import com.actionbarsherlock.app.SherlockFragmentActivity
import android.support.v4.app.Fragment
import android.view.View
import net.routestory.StoryApplication
import android.content.Context
import scala.util.continuations._
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.app.SherlockFragmentActivity
import com.actionbarsherlock.app.SherlockFragmentActivity

trait StoryUI {
	implicit class RichFuture[A](val value: Future[A]) {
	    def onSuccessUI(f: PartialFunction[A, Any])(implicit c: ExecutionContext): Future[A] = {
	        value onSuccess { case v =>
	            runOnUiThread {
	                f.lift(v)
	            }
	        }
	        value
	    }
	    def onFailureUI(f: PartialFunction[Throwable, Any])(implicit c: ExecutionContext): Future[A] = {
	        value onFailure { case v =>
	            runOnUiThread {
	                f.lift(v)
	            }
	        }
	        value
	    }
	    def mapUI(f: A => Any)(implicit c: ExecutionContext): Future[Unit] = {
	        value map { v =>
	            runOnUiThread {
	                f(v)
	            }
	        }
	    }
	}

    import akka.dataflow.DataflowFuture
    def await[A](f: Future[A]) = f.apply()

    def switchToUiThread(): Unit @cps[Future[Any]] = shift { f: (Unit ⇒ Future[Any]) ⇒
        val uiPromise = Promise[Any]()
        handler.post(new Runnable {
            def run() { uiPromise.completeWith(f()) }
        })
        uiPromise.future
    }

    implicit def cacher2Future[A](c: Cacher[A])(implicit ctx: Context): Future[A] = {
        if (c.isCached(ctx)) {
            Future.successful(c.get(ctx))
        } else future {
            c.cache(ctx)
        	c.get(ctx)
        }
    }
    implicit def cacher2RichFuture[A](c: Cacher[A])(implicit ctx: Context): RichFuture[A] = cacher2Future(c)
}

trait StoryActivity extends SherlockFragmentActivity with StoryUI with SActivity {
    lazy val app = getApplication.asInstanceOf[StoryApplication]
    lazy val bar = getSupportActionBar
    
    def findView[A <: View](id: Int) = findViewById(id).asInstanceOf[A]
    def findFrag[A <: Fragment](tag: String) = getSupportFragmentManager.findFragmentByTag(tag).asInstanceOf[A]
    
    var everStarted = false    
    def onFirstStart() {}
    def onEveryStart() {}
    override def onStart() {
        super.onStart()
        if (!everStarted) {
            onFirstStart()
            everStarted = true
        }
        onEveryStart()
    }
}

trait StoryFragment extends Fragment with StoryUI {
    lazy val app = getActivity.getApplication.asInstanceOf[StoryApplication]
    implicit lazy val ctx = getActivity
    
    def findView[A <: View](id: Int) = getView.findViewById(id).asInstanceOf[A]
    def findFrag[A <: Fragment](tag: String) = getChildFragmentManager.findFragmentByTag(tag).asInstanceOf[A]
    
    var everStarted = false    
    def onFirstStart() {}
    def onEveryStart() {}
    override def onStart() {
        super.onStart()
        if (!everStarted) {
            onFirstStart()
            everStarted = true
        }
        onEveryStart()
    }
}