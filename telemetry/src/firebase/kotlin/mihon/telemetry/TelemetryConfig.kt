package mihon.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context, isPreviewBuildType: Boolean, commitCount: String) {
        // To stop forks/test builds from polluting our data
        // MIKO -->
        if (!context.isMikoProductionApp()) return
        // MIKO <--

        analytics = FirebaseAnalytics.getInstance(context)
        FirebaseApp.initializeApp(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        // KMK -->
        if (isPreviewBuildType) {
            analytics?.setUserProperty("preview_version", commitCount)
        }
        // KMK <--
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    // MIKO -->
    private fun Context.isMikoProductionApp(): Boolean {
        if (packageName !in MIKO_PACKAGES) return false

        // No release keystore yet: without a fingerprint to compare against we cannot tell a
        // production build from a fork, so telemetry stays off instead of throwing.
        if (MIKO_CERTIFICATE_FINGERPRINT.isEmpty()) return false

        return packageManager.getPackageInfo(packageName, SignatureFlags)
            .getCertificateFingerprints()
            .any { it == MIKO_CERTIFICATE_FINGERPRINT }
    }
    // MIKO <--
}

// MIKO -->
private val MIKO_PACKAGES = hashSetOf("app.miko", "app.miko.beta")

// SHA-256 of the Miko release certificate (KKUP/miko.keystore, alias "miko",
// created 2026-08-17). Format must match [getCertificateFingerprint]: upper-case, ':'-separated.
private const val MIKO_CERTIFICATE_FINGERPRINT =
    "4D:17:CE:99:DC:8F:2C:2F:DA:B7:4C:42:9B:37:FC:ED:E9:BA:DD:8F:EC:8B:4A:1C:8A:AC:F2:9D:C2:07:8F:81"
// MIKO <--
