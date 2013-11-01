package net.routestory

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.commons.io.IOUtils

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.{ View, ViewGroup }
import android.widget._
import net.routestory.parts.{ Retry, GotoDialogFragments, HapticButton, RouteStoryActivity }
import net.routestory.parts.Styles._
import net.routestory.parts.Implicits._
import org.macroid.contrib.Layouts.VerticalLinearLayout
import scala.async.Async.{ async, await }
import android.util.Log

class AccountActivity extends RouteStoryActivity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    bar.setDisplayHomeAsUpEnabled(true)
    setContentView(l[FrameLayout]())
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
      w[HapticButton] ~> text(R.string.signin) ~> On.click(selectAccount())
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
    //val progress = spinnerDialog("", ctx.getResources.getString(R.string.message_preparingaccount))
    async {
      await(Retry.backoff(max = 100)(app.localContains(app.authorId.now.get)))
      //Ui(progress.dismiss())
      val author = await(app.getAuthor).get
      Ui(setContentView(l[ScrollView](
        l[VerticalLinearLayout](
          w[ImageView] ~> { x ⇒
            x.setScaleType(ImageView.ScaleType.FIT_START)
            x.setAdjustViewBounds(true)
            author.pictureCache.get foreachUi {
              case bitmap if bitmap != null ⇒ x.setImageBitmap(bitmap)
              case _ ⇒ x.setImageResource(R.drawable.ic_launcher)
            }
          },
          w[TextView] ~> text(author.name) ~> TextSize.large,
          w[HapticButton] ~> text(R.string.signout) ~> On.click {
            app.signOut
            showSignedOut()
          }
        )
      )))
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
          Log.d("AUTH", token)
          //val progress = spinnerDialog("", ctx.getResources.getString(R.string.toast_signingin))
          async {
            val url = new URL("https://www-routestory-net.herokuapp.com/signin?mobiletoken=" + URLEncoder.encode(token, "UTF-8"))
            val response = await(async {
              val conn = url.openConnection().asInstanceOf[HttpURLConnection]
              IOUtils.toString(conn.getInputStream)
            })
            // the response is: author_id;hashed_authentication_token
            await(app.setAuthData(Some(response.split(";"))))
            //Ui(progress.dismiss())
            Ui(showSignedIn())
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
