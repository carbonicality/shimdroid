@file:Suppress("DEPRECATION") // GoogleSignIn is still the supported way to get API scopes on Wear OS.

package dev.openanalytics.wear.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dev.openanalytics.wear.data.ANALYTICS_READONLY_SCOPE
import dev.openanalytics.wear.data.AccessTokenSource
import dev.openanalytics.wear.data.ConfigException

/** Not signed in, or signed in without the Analytics scope. */
class NotSignedInException : ConfigException("Sign in with your Google account in the app")

/** Google needs the user to approve access; launch [intent] from an Activity. */
class ConsentRequiredException(val intent: Intent?) : Exception("Allow openAnalytics to read your Analytics data")

/**
 * Uses the Google account already on the watch. Tokens come from Google Play
 * services (GoogleAuthUtil), which caches and refreshes them for us.
 */
object GoogleAuth {
    private val scope = Scope(ANALYTICS_READONLY_SCOPE)

    fun client(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(scope)
                .build(),
        )

    fun signInIntent(context: Context): Intent = client(context).signInIntent

    /** Returns the signed-in account from a sign-in activity result, or throws with a readable reason. */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount =
        try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            throw ConfigException(signInError(e.statusCode))
        }

    /** The signed-in account, if it has granted the Analytics scope. */
    fun account(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf { GoogleSignIn.hasPermissions(it, scope) }

    fun tokenSource(context: Context): AccessTokenSource {
        val acc = account(context)?.account ?: throw NotSignedInException()
        return PlayServicesTokenSource(context.applicationContext, acc)
    }

    fun signOut(context: Context) {
        client(context).signOut()
    }

    private fun signInError(code: Int): String = when (code) {
        12501 -> "Sign-in cancelled"
        7 -> "No network. Check the watch's connection"
        10 -> "Sign-in rejected (DEVELOPER_ERROR): the OAuth client's package name or SHA-1 " +
            "doesn't match this APK. See README → Google Cloud setup"
        else -> "Sign-in failed (code $code)"
    }

    private class PlayServicesTokenSource(
        private val context: Context,
        private val account: Account,
    ) : AccessTokenSource {
        override fun token(): String =
            try {
                GoogleAuthUtil.getToken(context, account, "oauth2:$ANALYTICS_READONLY_SCOPE")
            } catch (e: UserRecoverableAuthException) {
                throw ConsentRequiredException(e.intent)
            }

        override fun invalidate(token: String) {
            runCatching { GoogleAuthUtil.clearToken(context, token) }
        }
    }
}
