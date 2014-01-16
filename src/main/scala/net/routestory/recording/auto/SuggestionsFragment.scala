package net.routestory.recording.auto

import android.support.v4.app.ListFragment
import net.routestory.ui.RouteStoryFragment
import org.macroid.FullDsl._
import org.macroid.{ AppContext, ActivityContext }
import org.macroid.contrib.ListAdapter
import android.view.{ ViewGroup, View }
import android.widget.{ TextView, LinearLayout }
import net.routestory.util.FragmentData
import akka.actor.ActorSystem

case class Suggestion(title: String, subtitle: String)

class SuggestionsFragment extends ListFragment with RouteStoryFragment with FragmentData[ActorSystem] {
  import SuggestionsFragment._

  lazy val typewriter = getFragmentData.actorSelection("/user/typewriter")
  lazy val cartographer = getFragmentData.actorSelection("/user/cartographer")

  override def onStart() {
    super.onStart()
    val adapter = new Adapter
    adapter.addAll(Suggestion("qwe", "asd"), Suggestion("uio", "poi"))
    setListAdapter(adapter)
  }
}

object SuggestionsFragment {
  case class Adapter(implicit ctx: ActivityContext, appCtx: AppContext) extends ListAdapter[Suggestion, TextView] {
    def makeView = w[TextView]
    def fillView(view: TextView, parent: ViewGroup, data: Suggestion) = view ~> text(data.title)
  }
}
