package net.routestory.display

import java.io.IOException
import java.nio.charset.Charset

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import android.app.PendingIntent
import android.content.{ Intent, IntentFilter }
import android.net.Uri
import android.nfc.{ FormatException, NdefMessage, NdefRecord, NfcAdapter, Tag }
import android.nfc.tech.{ Ndef, NdefFormatable }
import android.os.Bundle
import android.view.{ Menu, MenuItem }
import android.widget.ProgressBar

import org.macroid.FullDsl._
import org.macroid.contrib.Layouts.VerticalLinearLayout

import net.routestory.R
import net.routestory.model._
import net.routestory.ui.{ FragmentPaging, RouteStoryActivity }
import net.routestory.util._
import org.macroid.IdGeneration

object DisplayActivity {
  object NfcIntent {
    def unapply(i: Intent) = Option(i).filter(_.getAction == NfcAdapter.ACTION_NDEF_DISCOVERED).flatMap { intent ⇒
      Option(intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) map { rawMsgs ⇒
        val msg = rawMsgs(0).asInstanceOf[NdefMessage]
        val rec = msg.getRecords()(0)
        Uri.parse(new String(rec.getPayload))
      }
    }
  }
  object ViewIntent {
    def unapply(i: Intent) = Option(i).filter(_.getAction == Intent.ACTION_VIEW).map(_.getData)
  }
  object PlainIntent {
    def unapply(i: Intent) = Option(i).filter(_.hasExtra("id")).map(_.getStringExtra("id"))
  }
}

class DisplayActivity extends RouteStoryActivity with FragmentDataProvider[Future[Story]] with FragmentPaging with IdGeneration {
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

  lazy val story = app.api.story(id).go
  lazy val media = story map { s ⇒
    s.chapters(0).media flatMap {
      case m: Story.HeavyMedia ⇒ m.data :: Nil
      case _ ⇒ Nil
    }
  }

  def getFragmentData(tag: String) = story

  var progress = slot[ProgressBar]
  var shareable = false
  lazy val nfcAdapter: Option[NfcAdapter] = Option(NfcAdapter.getDefaultAdapter(getApplicationContext))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    media // start loading

    setContentView(drawer(
      l[VerticalLinearLayout](
        activityProgress ~> wire(progress),
        getTabs(
          "Dive" → f[DiveFragment].factory,
          "Details" → f[DetailsFragment].factory,
          "Flat view" → f[FlatFragment].factory
        )
      )
    ))

    //    Retry.backoff(max = 10)(app.remoteContains(id)) foreachUi { _ ⇒
    //      shareable = true
    //      invalidateOptionsMenu()
    //    }

    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)
    progress ~@> waitProgress(story) ~@> media.map(waitProgress)

    story mapUi { s ⇒
      bar.setTitle(s.meta.title.filter(!_.isEmpty).getOrElse(getResources.getString(R.string.untitled)))
      bar.setSubtitle("by " + s.author.map(_.name).getOrElse("me"))
    } onFailureUi {
      case t ⇒
        t.printStackTrace()
        toast("Failed to load the story") ~> fry
        finish()
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_display, menu)
    if (nfcAdapter.isEmpty) {
      menu.findItem(R.id.storeNfc).setEnabled(false)
    }
    //    app.localContains(id) foreachUi {
    //      case false ⇒ menu.findItem(R.id.deleteStory).setVisible(false)
    //      case true ⇒
    //    }
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
      case R.id.storeNfc ⇒
        val intent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[DisplayActivity]).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
        val filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techs = Array(Array(classOf[NdefFormatable].getName, classOf[Ndef].getName))
        nfcAdapter.foreach(_.enableForegroundDispatch(this, intent, Array(filter), techs))
        toast("Waiting for the tag...") ~> fry // TODO: strings.xml
        true
      case R.id.shareStory ⇒
        val intent = new Intent(Intent.ACTION_SEND)
          .setType("text/plain")
          .putExtra(Intent.EXTRA_SUBJECT, getResources.getString(R.string.share_subject))
          .putExtra(
            Intent.EXTRA_TEXT,
            getResources.getText(R.string.share_body) + " http://www.routestory.net/" + id.replace("-", "/")
          )
        startActivity(Intent.createChooser(intent, getResources.getString(R.string.share_chooser)))
        true
      //      case R.id.deleteStory ⇒
      //        new AlertDialog.Builder(this) {
      //          setMessage(R.string.message_deletestory)
      //          setPositiveButton(android.R.string.yes, async {
      //            val s = await(story)
      //            await(app.deleteStory(s))
      //            app.requestSync
      //            finish()
      //            val intent = new Intent(ctx, classOf[ExploreActivity]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      //            startActivity(intent)
      //          })
      //          setNegativeButton(android.R.string.no, ())
      //        }.create().show()
      //        true
      case android.R.id.home ⇒ super[RouteStoryActivity].onOptionsItemSelected(item)
      case _ ⇒ false
    }
  }

  override def onNewIntent(intent: Intent) {
    toast("Found a tag, writing...") ~> fry
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
    toast("Done!") ~> fry
  }

  override def onPause() {
    super.onPause()
    nfcAdapter.foreach(_.disableForegroundDispatch(this))
  }
}
