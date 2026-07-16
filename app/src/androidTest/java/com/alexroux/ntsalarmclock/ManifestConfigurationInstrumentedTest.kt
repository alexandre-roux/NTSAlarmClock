package com.alexroux.ntsalarmclock

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alexroux.ntsalarmclock.playback.PlaybackService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented checks for manifest configuration that affects runtime behavior.
 *
 * These are device tests because PackageManager exposes the merged manifest as
 * Android actually installed it, including permissions and service metadata.
 *
 * This is more reliable than reading the source `AndroidManifest.xml`: Gradle can merge entries from
 * build variants and libraries before installation. The small helper methods use the modern typed flag
 * APIs on Android 13+ and deprecated integer flags on older versions so the same assertions work across
 * the app's supported API levels.
 */
@RunWith(AndroidJUnit4::class)
class ManifestConfigurationInstrumentedTest {

    /**
     * Verifies the installed manifest keeps playback private to the app and, where supported,
     * identifies it as a media-playback foreground service.
     *
     * [ServiceInfo.exported] must be false so other apps cannot send commands to the alarm playback
     * service. Android 10+ exposes foreground-service type metadata; the bit-mask assertion checks that
     * `mediaPlayback` is included even if future manifest changes add other valid types alongside it.
     */
    @Test
    fun playbackService_isPrivateMediaPlaybackForegroundService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceInfo = playbackServiceInfo(context)

        // The service should not be externally startable, and on modern Android
        // it must declare the mediaPlayback foreground-service type.
        assertFalse(serviceInfo.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(
                serviceInfo.foregroundServiceType and
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0
            )
        }
    }

    /**
     * Verifies the merged manifest requests every permission needed to restore alarms, post
     * notifications, schedule exact alarms, and run foreground playback.
     *
     * PackageManager returns the permissions from the actually installed APK. Converting the array to a
     * set makes membership assertions independent of manifest order. Each permission protects a distinct
     * runtime path: boot restoration, notification visibility, exact wake-up timing, general foreground
     * service use, and the media-playback service subtype.
     */
    @Test
    fun manifestRequestsAlarmNotificationAndForegroundServicePermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = packageInfo(context).requestedPermissions.orEmpty().toSet()

        // These permissions are the contract that lets alarms survive reboot,
        // post visible alarm notifications, and run playback as a foreground service.
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertTrue(Manifest.permission.USE_EXACT_ALARM in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK in permissions)
    }

    private fun playbackServiceInfo(context: Context): ServiceInfo {
        // PackageManager changed from integer flags to type-safe flag wrappers in API 33. Both branches
        // request the same installed service record; the version check only adapts the API call shape.
        val component = ComponentName(context, PlaybackService::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, 0)
        }
    }

    private fun packageInfo(context: Context): PackageInfo {
        // GET_PERMISSIONS asks PackageManager to populate requestedPermissions in the returned record.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
        }
    }
}
