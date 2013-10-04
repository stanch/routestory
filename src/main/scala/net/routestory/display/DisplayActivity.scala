package net.routestory.display

import java.io.IOException
import java.nio.charset.Charset

import scala.concurrent.ExecutionContext.Implicits.global

import org.scaloid.common._

import android.app.{ AlertDialog, PendingIntent }
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.view.{ Menu, MenuItem }
import android.widget.{ ProgressBar, Toast }
import net.routestory.R
import net.routestory.model._
import net.routestory.parts._
import scala.concurrent._
import scala.async.Async.{ async, await }
import org.macroid.util.Text
import net.routestory.explore.ExploreActivity
import net.routestory.lounge.Patterns
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.collection.JavaConversions._
import io.dylemma.frp.Observer
import java.util.concurrent.Executors
import Styles._

object DisplayActivity {
  object NfcIntent {
    def unapply(i: Intent): Option[Uri] = if (i.getAction == NfcAdapter.ACTION_NDEF_DISCOVERED) {
      Option(i.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) map { rawMsgs ⇒
        val msg = rawMsgs(0).asInstanceOf[NdefMessage]
        val rec = msg.getRecords()(0)
        new String(rec.getPayload)
      }
    } else None
  }
  object ViewIntent {
    def unapply(i: Intent): Option[Uri] = if (i.getAction == Intent.ACTION_VIEW) {
      Some(i.getData)
    } else None
  }
  object PlainIntent {
    def unapply(i: Intent): Option[String] = if (i.hasExtra("id")) Some(i.getStringExtra("id")) else None
  }
}

trait HazStory {
  val story: Future[Story]
  val media: Future[List[Future[Boolean]]]
}

class DisplayActivity extends RouteStoryActivity with HazStory with FragmentPaging with Observer {
  import DisplayActivity._

  private lazy val id = getIntent match {
    case NfcIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case ViewIntent(uri) ⇒
      "story-" + uri.getLastPathSegment
    case PlainIntent(i) ⇒
      i
    case _ ⇒
      finish(); ""
  }

  lazy val story = Patterns.getStory(app)(id)

  lazy val media = {
    // avoid clogging ForkJoinPool with blocking IO
    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    story map { s ⇒ s.photos.map(_.preload).toList ::: Option(s.audioPreview).map(_.preload :: Nil).getOrElse(Nil) }
  }

  var progress = slot[ProgressBar]
  var shareable = false
  lazy val nfcAdapter: Option[NfcAdapter] = Option(NfcAdapter.getDefaultAdapter(getApplicationContext))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(drawer(
      l[VerticalLinearLayout](
        activityProgress ~> wire(progress),
        getTabs(
          Text(R.string.title_tab_storypreview) → ff[PreviewFragment](),
          Text(R.string.title_tab_storydescription) → ff[DescriptionFragment](),
          Text(R.string.title_tab_storyoverview) → ff[OverviewFragment]()
        )
      )
    ))

    Retry.backoff(max = 10)(app.remoteContains(id)) foreachUi { _ ⇒
      shareable = true
      invalidateOptionsMenu()
    }

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    progress ~@> waitProgress(story) ~@> media.map(waitProgress)

    story mapUi { s ⇒
      bar.setTitle(Option(s.title).filter(!_.isEmpty).getOrElse(getResources.getString(R.string.untitled)))
      bar.setSubtitle("by " + Option(s.author).map(_.name).getOrElse("me"))
    } onFailureUi {
      case t ⇒
        t.printStackTrace()
        toast("Failed to load the story")
        finish()
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_display, menu)
    if (nfcAdapter.isEmpty) {
      menu.findItem(R.id.storeNfc).setEnabled(false)
    }
    app.localContains(id) foreachUi {
      case false ⇒ menu.findItem(R.id.deleteStory).setVisible(false)
      case true ⇒
    }
    true
  }

  override def onPrepareOptionsMenu(menu: Menu): Boolean = {
    menu.findItem(R.id.shareStory).setEnabled(shareable)
    menu.findItem(R.id.storeNfc).setEnabled(nfcAdapter.isDefined && shareable)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    super.onOptionsItemSelected(item)
    item.getItemId match {
      case R.id.storeNfc ⇒ {
        val intent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[DisplayActivity]).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
        val filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techs = Array(Array(classOf[NdefFormatable].getName, classOf[Ndef].getName))
        nfcAdapter.foreach(_.enableForegroundDispatch(this, intent, Array(filter), techs))
        toast("Waiting for the tag...") // TODO: strings.xml
        true
      }
      case R.id.shareStory ⇒ {
        val intent = new Intent(Intent.ACTION_SEND)
          .setType("text/plain")
          .putExtra(Intent.EXTRA_SUBJECT, getResources.getString(R.string.share_subject))
          .putExtra(
            Intent.EXTRA_TEXT,
            Text(R.string.share_body) + " http://www.routestory.net/" + id.replace("-", "/")
          )
        startActivity(Intent.createChooser(intent, getResources.getString(R.string.share_chooser)))
        true
      }
      case R.id.deleteStory ⇒ {
        new AlertDialog.Builder(this) {
          setMessage(R.string.message_deletestory)
          setPositiveButton(android.R.string.yes, async {
            val s = await(story)
            await(app.deleteStory(s))
            app.requestSync
            finish()
            val intent = SIntent[ExploreActivity].addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
          })
          setNegativeButton(android.R.string.no, ())
        }.create().show()
        true
      }
      case android.R.id.home ⇒ super[RouteStoryActivity].onOptionsItemSelected(item)
      case _ ⇒ false
    }
  }

  override def onSaveInstanceState(savedInstanceState: Bundle) {
    super.onSaveInstanceState(savedInstanceState)
    //savedInstanceState.putInt("tab", bar.getSelectedTab.getPosition)
  }

  override def onNewIntent(intent: Intent) {
    Toast.makeText(this, "Found a tag, writing...", Toast.LENGTH_SHORT).show()
    // TODO: strings.xml
    val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG).asInstanceOf[Tag]
    val uri = ("http://www.routestory.net/" + id.replace("story-", "story/")).getBytes(Charset.forName("US-ASCII"))
    val payload = new Array[Byte](uri.length + 1)
    payload(0) = 0.toByte
    System.arraycopy(uri, 0, payload, 1, uri.length)
    val rec = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI, new Array[Byte](0), payload)
    val msg = new NdefMessage(Array(rec))
    val ndef = Ndef.get(tag)
    try {
      ndef.connect()
      ndef.writeNdefMessage(msg)
      ndef.close()
    } catch {
      case e @ (_: FormatException | _: IOException) ⇒ e.printStackTrace()
    }
    toast("Done!") // TODO: strings.xml
  }

  override def onPause() {
    super.onPause()
    nfcAdapter.foreach(_.disableForegroundDispatch(this))
  }
}
