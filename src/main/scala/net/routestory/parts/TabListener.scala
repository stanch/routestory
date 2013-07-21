package net.routestory.parts

import scala.reflect.ClassTag
import android.app.{ FragmentTransaction, Fragment, ActionBar, Activity }

case class TabListener[A <: Fragment: ClassTag](activity: Activity, tag: String) extends ActionBar.TabListener {
  var fragment: Option[Fragment] = None

  override def onTabSelected(tab: ActionBar.Tab, ft: FragmentTransaction) {
    val preInitializedFragment = Option(activity.getFragmentManager.findFragmentByTag(tag))
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

