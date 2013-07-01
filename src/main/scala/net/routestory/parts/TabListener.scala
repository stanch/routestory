package net.routestory.parts

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import com.actionbarsherlock.app.ActionBar
import com.actionbarsherlock.app.ActionBar.Tab
import com.actionbarsherlock.app.SherlockFragmentActivity
import scala.reflect.ClassTag

case class TabListener[A <: Fragment : ClassTag](activity: SherlockFragmentActivity, tag: String) extends ActionBar.TabListener {
    var fragment: Option[Fragment] = None

    override def onTabSelected(tab: ActionBar.Tab, ft: FragmentTransaction) {
        val preInitializedFragment = Option(activity.getSupportFragmentManager.findFragmentByTag(tag).asInstanceOf[Fragment])
        (fragment, preInitializedFragment) match {
            case (None, None) ⇒
                fragment = Some(Fragment.instantiate(activity, implicitly[ClassTag[A]].runtimeClass.getName))
                ft.add(android.R.id.content, fragment.get, tag)
            case (Some(f), _) ⇒
                ft.attach(f)
            case (_, Some(f)) ⇒
                ft.attach(f)
                fragment = Some(f)
        }
    }
    override def onTabUnselected(tab: ActionBar.Tab, ft: FragmentTransaction) {
        fragment.foreach(ft.detach(_))
    }
    override def onTabReselected(tab: ActionBar.Tab, ft: FragmentTransaction) {}
}

