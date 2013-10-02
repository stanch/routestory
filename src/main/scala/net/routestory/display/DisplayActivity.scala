package net.routestory.display

import java.io.IOException
import java.nio.charset.Charset

import scala.concurrent.ExecutionContext.Implicits.global

import org.scaloid.common._

import android.app.{ ActionBar, AlertDialog, PendingIntent }
import android.content.DialogInterface
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
import android.view.{ Menu, MenuItem, Window }
import android.widget.FrameLayout
import android.widget.Toast
import net.routestory.R
import net.routestory.StoryApplication
import net.routestory.model._
import net.routestory.parts._
import scala.concurrent._
import scala.async.Async.{ async, await }
import org.macroid.util.Text
import net.routestory.explore.ExploreActivity

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
  def getStory: Future[Story]
}

class DisplayActivity extends StoryActivity with HazStory with FragmentPaging {
  import DisplayActivity._

  private var id: String = _

  private lazy val mStory = async {
    val story = await(app.getObject[Story](id))
    val author = await(app.getObject[Author](story.authorId))
    story.author = author
    story
  }
  def getStory = mStory

  var mShareable = false

  lazy val mNfcAdapter: Option[NfcAdapter] = Option(NfcAdapter.getDefaultAdapter(getApplicationContext))

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(new FrameLayout(this))

    id = getIntent match {
      case NfcIntent(uri) ⇒
        "story-" + uri.getLastPathSegment
      case ViewIntent(uri) ⇒
        "story-" + uri.getLastPathSegment
      case PlainIntent(i) ⇒
        i
      case _ ⇒
        finish()
        return
    }

    setProgressBarIndeterminateVisibility(true)

    mStory onSuccessUi {
      case _ ⇒ setProgressBarIndeterminateVisibility(false)
    } onFailureUi {
      case e ⇒
        e.printStackTrace()
        toast("Failed to load the story")
        finish()
    }

    Retry.backoff(max = 10)(app.remoteContains(id)) onSuccessUi {
      case _ ⇒
        mShareable = true
        invalidateOptionsMenu()
    }

    //bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS)
    bar.setDisplayShowHomeEnabled(true)
    bar.setDisplayHomeAsUpEnabled(true)

    mStory onSuccessUi {
      case story ⇒
        bar.setTitle(if (story.title != null && story.title.length() > 0) story.title else getResources.getString(R.string.untitled))
        bar.setSubtitle(if (story.author != null) "by " + story.author.name else "by me")
    }

    setContentView(drawer(getTabs(
      Text(R.string.title_tab_storypreview) → ff[PreviewFragment](),
      Text(R.string.title_tab_storydescription) → ff[DescriptionFragment](),
      Text(R.string.title_tab_storyoverview) → ff[OverviewFragment]()
    )))

    //    val sel = Option(savedInstanceState).map(_.getInt("tab")).getOrElse(0)
    //    bar.addTab(R.string.title_tab_storypreview, new PreviewFragment, Tag.preview, sel == 0)
    //    bar.addTab(R.string.title_tab_storydescription, new DescriptionFragment, Tag.description, sel == 1)
    //    bar.addTab(R.string.title_tab_storyoverview, new OverviewFragment, Tag.map, sel == 2)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.activity_display, menu)
    if (mNfcAdapter.isEmpty) {
      menu.findItem(R.id.storeNfc).setEnabled(false)
    }
    async {
      if (!await(app.localContains(id))) {
        Ui(menu.findItem(R.id.deleteStory).setVisible(false))
      }
    }
    menu.findItem(R.id.followStory).setVisible(false) // TODO: fix the follow mode!
    true
  }

  override def onPrepareOptionsMenu(menu: Menu): Boolean = {
    menu.findItem(R.id.shareStory).setEnabled(mShareable)
    menu.findItem(R.id.storeNfc).setEnabled(mNfcAdapter.isDefined && mShareable)
    true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    super.onOptionsItemSelected(item)
    item.getItemId match {
      case R.id.storeNfc ⇒ {
        val intent = PendingIntent.getActivity(this, 0, new Intent(this, classOf[DisplayActivity]).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
        val filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techs = Array(Array(classOf[NdefFormatable].getName, classOf[Ndef].getName))
        mNfcAdapter.foreach(_.enableForegroundDispatch(this, intent, Array(filter), techs))
        toast("Waiting for the tag...") // TODO: strings.xml
        true
        //			} case R.id.followStory => {
        //				val intent = SIntent[FollowActivity]
        //				intent.putExtra("id", id)
        //	            startActivityForResult(intent, 0)
        //	            true
      }
      case R.id.shareStory ⇒ {
        val intent = new Intent(Intent.ACTION_SEND)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_SUBJECT, getResources.getString(R.string.share_subject))
        intent.putExtra(
          Intent.EXTRA_TEXT,
          getResources.getString(R.string.share_body) + " http://www.routestory.net/" + id.replace("-", "/"))
        startActivity(Intent.createChooser(intent, getResources.getString(R.string.share_chooser)))
        true
      }
      case R.id.deleteStory ⇒ {
        new AlertDialog.Builder(this) {
          setMessage(R.string.message_deletestory)
          setPositiveButton(R.string.button_yes, { (dialog: DialogInterface, which: Int) ⇒
            mStory onSuccessUi {
              case story ⇒
                app.deleteStory(story)
                app.requestSync
                finish()
                val intent = SIntent[ExploreActivity]
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
          })
          setNegativeButton(R.string.button_no, { (dialog: DialogInterface, which: Int) ⇒
            // pass
          })
        }.create().show()
        true
      }
      case android.R.id.home ⇒ super[StoryActivity].onOptionsItemSelected(item)
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
    mNfcAdapter.foreach(_.disableForegroundDispatch(this))
  }
}
