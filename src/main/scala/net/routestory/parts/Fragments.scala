package net.routestory.parts

import scala.concurrent.Promise
import android.support.v4.app.Fragment

trait FragmentDataProvider[A] {
  def getFragmentData(tag: String): A
}

trait FragmentData[A] { self: Fragment ⇒
  def getFragmentData = getActivity.asInstanceOf[FragmentDataProvider[A]].getFragmentData(getTag)
}
