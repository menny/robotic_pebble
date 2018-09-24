package net.evendanan.robotic_pebble

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import net.evendanan.chauffeur.lib.permissions.PermissionsRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

const val PEBBLE_APP_PACKAGE = "com.getpebble.android.basalt"
const val PEBBLE_MAIN_ACTIVITY = "com.getpebble.android.main.activity.MainActivity"

interface UserNotification {
    fun closeUi()
    fun showOperationText(text: CharSequence)
    fun showPebbleAppInstall(pebbleAppPackage: String)

    fun askForAndroidPermission(request: PermissionsRequest)
}

class FileProxy(private val userNotification: UserNotification) {
    private var job: Job? = null

    fun handleIntent(context: Context, intent: Intent) {
        Log.d("FileProxy", "Intent $intent")
        when {
            intent.data != null -> intent.data
            intent.hasExtra(Intent.EXTRA_STREAM) -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }?.let { receivedUri ->
            Log.d("FileProxy", "Resolved Intent's URI $receivedUri, scheme: ${receivedUri.scheme}")

            job?.cancel()

            job = launch(Unconfined) {
                userNotification.showOperationText(context.getString(R.string.requesting_android_permission))

                val proxyPermissions = ProxyPermissions()
                userNotification.askForAndroidPermission(proxyPermissions)
                val hasPermissions = async(CommonPool) { proxyPermissions.waitForResponse() }.await()

                if (hasPermissions) {
                    userNotification.showOperationText(context.getString(R.string.loading_file_to_local_storage))

                    val fileUri = async(CommonPool) {
                        when (receivedUri.scheme) {
                            null -> Uri.fromParts("file", receivedUri.path, "")//going to assume this is a local file
                            else -> receivedUri //"file","http", "https", "content" or anything else...
                        }.apply { proxyUriToLocalFileUri(context, this) }
                    }.await()

                    userNotification.showOperationText(context.getString(R.string.send_file_to_pebble_app))

                    try {
                        context.startActivity(
                                Intent(Intent.ACTION_VIEW, fileUri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    component = ComponentName(PEBBLE_APP_PACKAGE, PEBBLE_MAIN_ACTIVITY)
                                })
                        userNotification.closeUi()
                    } catch (missingActivity: ActivityNotFoundException) {
                        userNotification.showOperationText(context.getString(R.string.error_pebble_app_not_installed))
                        userNotification.showPebbleAppInstall(PEBBLE_APP_PACKAGE)
                    }
                } else {
                    userNotification.showOperationText(context.getString(R.string.error_permission_not_granted))
                }
            }
            job?.invokeOnCompletion { ex ->
                ex?.run {
                    printStackTrace()
                    userNotification.showOperationText(context.getString(R.string.error_failed_to_perform_proxy, this))
                }
            }
        }
                ?: userNotification.showOperationText(context.getString(R.string.error_intent_data_unrecognized))
    }

    fun destroy() {
        job?.cancel()
    }

}

private class ProxyPermissions : PermissionsRequest {

    private val success = AtomicBoolean(false)
    private val latch = CountDownLatch(1)

    override fun onPermissionsDenied(grantedPermissions: Array<out String>, deniedPermissions: Array<out String>, declinedPermissions: Array<out String>) {
        success.set(false)
        latch.countDown()
    }

    override fun getRequestedPermissions() = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET)

    override fun onPermissionsGranted() {
        success.set(true)
        latch.countDown()
    }

    override fun getRequestCode() = 111

    fun waitForResponse(): Boolean {
        latch.await()
        return success.get()
    }
}

private fun proxyUriToLocalFileUri(context: Context, uriToProxyLocally: Uri): Uri {
    Log.d("FileProxy", "proxying $uriToProxyLocally locally...")
    context.contentResolver.openInputStream(uriToProxyLocally).use { receivedInputStream ->
        File(Environment.getExternalStorageDirectory(), "PebbleTransfer").run {
            if ((exists() && isDirectory) || mkdirs()) {
                val targetFile = File(this, uriToProxyLocally.lastPathSegment)
                targetFile.outputStream().use {
                    receivedInputStream.copyTo(it)
                }

                Log.d("FileProxy", "Copied remote data to ${targetFile.absolutePath}")
                return Uri.fromFile(targetFile)
            } else {
                throw IOException("Was not able to create PebbleTransfer folder!")
            }
        }
    }
}
