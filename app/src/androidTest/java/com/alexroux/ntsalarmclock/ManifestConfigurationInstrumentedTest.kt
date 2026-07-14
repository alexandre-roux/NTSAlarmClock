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
 */
@RunWith(AndroidJUnit4::class)
class ManifestConfigurationInstrumentedTest {

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
