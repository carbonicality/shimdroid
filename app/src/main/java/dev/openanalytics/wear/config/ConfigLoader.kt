package dev.openanalytics.wear.config

import android.content.Context
import dev.openanalytics.wear.BuildConfig
import dev.openanalytics.wear.data.AnalyticsClient
import dev.openanalytics.wear.data.ConfigException
import dev.openanalytics.wear.data.ServiceAccount
import java.io.File

data class AnalyticsConfig(val propertyId: String, val serviceAccount: ServiceAccount)

/**
 * Finds credentials, checking in this order:
 *  1. internal files dir      (/data/data/<pkg>/files)         - via `adb shell run-as`
 *  2. app external files dir  (/sdcard/Android/data/<pkg>/files) - via `adb push`
 *  3. bundled asset           (app/src/main/assets/service-account.json, build-time)
 *
 * The property ID comes from property-id.txt in the same places, falling back to
 * BuildConfig.GA_PROPERTY_ID (ga.propertyId in local.properties).
 */
object ConfigLoader {
    const val SERVICE_ACCOUNT_FILE = "service-account.json"
    const val PROPERTY_ID_FILE = "property-id.txt"

    fun load(context: Context): AnalyticsConfig {
        val dirs = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))

        val saJson = dirs.firstNotNullOfOrNull { readIfExists(File(it, SERVICE_ACCOUNT_FILE)) }
            ?: readAsset(context, SERVICE_ACCOUNT_FILE)
            ?: throw ConfigException("No $SERVICE_ACCOUNT_FILE found. Provision it with scripts/provision.sh")

        val rawId = dirs.firstNotNullOfOrNull { readIfExists(File(it, PROPERTY_ID_FILE)) }
            ?: readAsset(context, PROPERTY_ID_FILE)
            ?: BuildConfig.GA_PROPERTY_ID.ifBlank { null }
            ?: throw ConfigException("No GA4 property ID set. Provision $PROPERTY_ID_FILE with scripts/provision.sh")

        return AnalyticsConfig(
            propertyId = AnalyticsClient.normalizePropertyId(rawId),
            serviceAccount = ServiceAccount.fromJson(saJson),
        )
    }

    private fun readIfExists(f: File): String? =
        runCatching { if (f.isFile) f.readText().trim().ifBlank { null } else null }.getOrNull()

    private fun readAsset(context: Context, name: String): String? =
        runCatching { context.assets.open(name).bufferedReader().use { it.readText() }.trim().ifBlank { null } }
            .getOrNull()
}
