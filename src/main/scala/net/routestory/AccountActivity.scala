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
import net.routestory.parts.Styles._
import org.macroid.Layouts.VerticalLinearLayout

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

  def showSignedOut() {
    setContentView(l[VerticalLinearLayout](
      w[TextView] ~> text(R.string.account_policy) ~> TextSize.medium,
      w[HapticButton] ~> text(R.string.signin) ~> (_.setOnClickListener(selectAccount))
    ))
  }

  def selectAccount() {
    val accountManager = AccountManager.get(ctx)
    val accounts = accountManager.getAccountsByType("com.google")
    // ask which one to use
    new AlertDialog.Builder(ctx) {
      setTitle(R.string.title_dialog_selectaccount)
      setAdapter(new ArrayAdapter[Account](ctx, 0, accounts) {
        override def getView(position: Int, itemView: View, parent: ViewGroup): View = {
          val view = Option(itemView).getOrElse(l[LinearLayout](
            w[TextView] ~> id(Id.account) ~> TextSize.large
          ))
          findView[TextView](view, Id.account) ~> text(accounts(position).name)
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
      setContentView(l[ScrollView](
        l[VerticalLinearLayout](
          w[ImageView] ~> { x ⇒
            x.setScaleType(ImageView.ScaleType.FIT_START)
            x.setAdjustViewBounds(true)
            author.pictureCache.get onSuccessUi {
              case bitmap if bitmap != null ⇒ x.setImageBitmap(bitmap)
              case _ ⇒ x.setImageResource(R.drawable.ic_launcher)
            }
          },
          w[TextView] ~> text(author.name) ~> TextSize.large,
          w[HapticButton] ~> text(R.string.signout) ~> { x ⇒
            x.setOnClickListener { v: View ⇒
              app.signOut()
              showSignedOut()
            }
          }
        )
      ))
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
