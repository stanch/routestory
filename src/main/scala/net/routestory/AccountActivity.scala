package net.routestory

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.future

import org.apache.commons.io.IOUtils
import org.scaloid.common._

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.{ MenuItem, View, ViewGroup }
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.routestory.parts.GotoDialogFragments
import net.routestory.parts.HapticButton
import net.routestory.parts.StoryActivity
import akka.dataflow._
import android.util.Log

class AccountActivity extends StoryActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(new SFrameLayout())
  }

  override def onStart() {
    super.onStart()
    if (app.isSignedIn) {
      showSignedIn()
    } else if (GotoDialogFragments.ensureNetwork(this)) {
      showSignedOut()
    }
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home ⇒
        val intent = SIntent[MainActivity]
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        true
      case _ ⇒
        super.onOptionsItemSelected(item)
    }
  }

  def showSignedOut() {
    val view = new SLinearLayout {
      setOrientation(LinearLayout.VERTICAL)
      this += new TextView(ctx) {
        setText(R.string.account_policy)
        setTextAppearance(ctx, android.R.style.TextAppearance_Medium)
      }
      this += new HapticButton(ctx) {
        setText(R.string.signin)
        setOnClickListener(selectAccount)
      }
    }

    setContentView(view)
  }

  def selectAccount() {
    val accountManager = AccountManager.get(ctx)
    val accounts = accountManager.getAccountsByType("com.google")
    // ask which one to use
    new AlertDialog.Builder(ctx) {
      setTitle(R.string.title_dialog_selectaccount)
      setAdapter(new ArrayAdapter[Account](ctx, 0, R.id.textView1, accounts) {
        override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
          val view = Option(itemView).getOrElse(new LinearLayout(ctx) {
            this += new TextView(ctx) {
              setTextAppearance(ctx, android.R.style.TextAppearance_Large)
              setId(1)
            }
          })
          view.findViewById(1).asInstanceOf[TextView].setText(accounts(position).name)
          view
        }
      }, { (dialog: DialogInterface, which: Int) ⇒
        // proceed to signing in
        signIn(accounts(which))
      })
      create()
      show()
    }
  }

  def showSignedIn() {
    val progress = spinnerDialog("", ctx.getResources.getString(R.string.message_preparingaccount))
    flow {
      await(future { while (!app.localContains(app.getAuthorId)) {} })
      switchToUiThread()
      progress.dismiss()
      val author = app.getAuthor
      val view = new ScrollView(ctx) {
        this += new LinearLayout(ctx) {
          setOrientation(LinearLayout.VERTICAL)
          this += new ImageView(ctx) {
            setScaleType(ImageView.ScaleType.FIT_START)
            setAdjustViewBounds(true)
            author.pictureCache.get onSuccessUi {
              case bitmap if bitmap != null ⇒ setImageBitmap(bitmap)
              case _ ⇒ setImageResource(R.drawable.ic_launcher)
            }
          }
          this += new TextView(ctx) {
            setText(author.name)
            setTextAppearance(AccountActivity.this, android.R.style.TextAppearance_Large)
          }
          this += new HapticButton(ctx) {
            setText(R.string.signout)
            setOnClickListener { v: View ⇒
              app.signOut()
              showSignedOut()
            }
          }
        }
      }
      setContentView(view)
    } onFailureUi {
      case e ⇒ e.printStackTrace()
    }
  }

  def signIn(account: Account) {
    // request authorization token from Google and send it to the server
    val accountManager = AccountManager.get(ctx)
    accountManager.getAuthToken(
      account, "oauth2:https://www.googleapis.com/auth/userinfo.profile",
      new Bundle(), AccountActivity.this,
      new AccountManagerCallback[Bundle]() {
        override def run(result: AccountManagerFuture[Bundle]) {
          val bundle = result.getResult
          val launch = bundle.get(AccountManager.KEY_INTENT).asInstanceOf[Intent]
          if (launch != null) {
            startActivityForResult(launch, 0) // TODO: should be handled, see how-to oAuth2 on Android
            return
          }
          val token = bundle.getString(AccountManager.KEY_AUTHTOKEN) // cool, we have a token
          val progress = spinnerDialog("", ctx.getResources.getString(R.string.toast_signingin))
          flow {
            val url = new URL("https://www-routestory-net.herokuapp.com/signin?mobiletoken=" + URLEncoder.encode(token, "UTF-8"))
            val response = await(flow {
              val conn = url.openConnection().asInstanceOf[HttpURLConnection]
              IOUtils.toString(conn.getInputStream)
            })
            switchToUiThread()
            // the response is: author_id;hashed_authentication_token
            app.setAuthData(response.split(";"))
            progress.dismiss()
            showSignedIn()
          } onFailureUi {
            case t ⇒
              accountManager.invalidateAuthToken("com.google", token)
              signIn(account)
          }
        }
      },
      new Handler())
  }
}
