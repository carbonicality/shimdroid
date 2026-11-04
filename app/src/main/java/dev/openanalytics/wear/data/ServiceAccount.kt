package dev.openanalytics.wear.data

import org.json.JSONException
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** The parts of a Google Cloud service-account JSON key that we need. */
class ServiceAccount(
    val clientEmail: String,
    val privateKey: PrivateKey,
    val tokenUri: String = DEFAULT_TOKEN_URI,
) {
    companion object {
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"

        fun fromJson(json: String): ServiceAccount {
            val o = try {
                JSONObject(json)
            } catch (e: JSONException) {
                throw ConfigException("Service account file is not valid JSON")
            }
            if (o.optString("type") != "service_account") {
                throw ConfigException("Expected a service account key (\"type\": \"service_account\")")
            }
            val email = o.optString("client_email").ifBlank {
                throw ConfigException("Service account key has no client_email")
            }
            val pem = o.optString("private_key").ifBlank {
                throw ConfigException("Service account key has no private_key")
            }
            val key = try {
                parsePrivateKey(pem)
            } catch (e: Exception) {
                throw ConfigException("Could not read private_key: ${e.message}")
            }
            return ServiceAccount(email, key, o.optString("token_uri").ifBlank { DEFAULT_TOKEN_URI })
        }

        fun parsePrivateKey(pem: String): PrivateKey {
            val b64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), "")
            val der = Base64.getDecoder().decode(b64)
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        }
    }
}

/**
 * Exchanges a signed JWT for an OAuth access token (the service-account
 * "JWT bearer" flow) and caches it until shortly before it expires.
 */
class TokenProvider(
    private val account: ServiceAccount,
    private val http: Http = Http.Default,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private var token: String? = null
    private var expiresAtMs = 0L

    @Synchronized
    fun accessToken(): String {
        val now = clockMs()
        token?.let { if (now < expiresAtMs - 60_000) return it }
        val res = http.postForm(
            account.tokenUri,
            mapOf("grant_type" to GRANT_TYPE, "assertion" to createAssertion(now / 1000)),
        )
        val t = res.optString("access_token").ifBlank {
            throw ApiException(500, "Token response had no access_token")
        }
        token = t
        expiresAtMs = now + res.optLong("expires_in", 3600) * 1000
        return t
    }

    fun createAssertion(nowSec: Long): String {
        val header = JSONObject().put("alg", "RS256").put("typ", "JWT")
        val claims = JSONObject()
            .put("iss", account.clientEmail)
            .put("scope", SCOPE)
            .put("aud", account.tokenUri)
            .put("iat", nowSec)
            .put("exp", nowSec + 3600)
        val signingInput = b64(header.toString().toByteArray()) + "." + b64(claims.toString().toByteArray())
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(account.privateKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return signingInput + "." + b64(sig)
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/analytics.readonly"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    }
}
