package net.routestory.parts

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.TextView
import android.content.DialogInterface

object Implicits {
	implicit def func2OnCameraChangeListener(f: CameraPosition => Any) = new GoogleMap.OnCameraChangeListener() {
	    def onCameraChange(p: CameraPosition) = f(p)
	}
	
	implicit def func2OnMarkerClickListener(f: Marker => Boolean) = new GoogleMap.OnMarkerClickListener() {
	    def onMarkerClick(m: Marker) = f(m)
	}
	
	implicit def func2OnGlobalLayoutListener(f: => Any) = new ViewTreeObserver.OnGlobalLayoutListener() {
	    def onGlobalLayout() = f
	}
	
	implicit def func2OnEditorActionListener(f: (TextView, Int, KeyEvent) => Boolean) = new TextView.OnEditorActionListener() {
	    def onEditorAction(v: TextView, a: Int, e: KeyEvent) = f(v, a, e)
	}
	
	implicit def func2OnDismissListener(f: DialogInterface => Any) = new DialogInterface.OnDismissListener() {
	    def onDismiss(d: DialogInterface) = f(d)
	}
	
	implicit def func2OnCancelListener(f: DialogInterface => Any) = new DialogInterface.OnCancelListener() {
		def onCancel(d: DialogInterface) = f(d)
	}

    import scala.collection.IterableLike
    import scala.util.continuations._

    // see http://stackoverflow.com/questions/8934226/continuations-and-for-comprehensions-whats-the-incompatibility
    implicit def cpsIterable[A, Repr](xs: IterableLike[A, Repr]) = new {
        def cps[B] = new {
            def foreach(f: A ⇒ Any @cpsParam[B, B]): Unit @cpsParam[B, B] = {
                val it = xs.iterator
                while(it.hasNext) f(it.next())
            }
        }
    }
}