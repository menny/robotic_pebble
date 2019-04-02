package net.evendanan.robotic_pebble

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.evendanan.chauffeur.lib.permissions.PermissionsRequest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

const val PEBBLE_APP_PACKAGE = "com.getpebble.android.basalt"
const val PEBBLE_MAIN_ACTIVITY = "com.getpebble.android.main.activity.MainActivity"

private const val LOGCAT_TAG = "FileProxy"

interface UserNotification {
    fun closeUi()
    fun showOperationText(text: CharSequence)
    fun showPebbleAppInstall(pebbleAppPackage: String)

    fun askForAndroidPermission(request: PermissionsRequest)
}

class FileProxy(private val userNotification: UserNotification) {
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main)

    fun handleIntent(context: Context, intent: Intent) {
        Log.d(LOGCAT_TAG, "Intent $intent")
        Log.d(LOGCAT_TAG, "Intent $intent with extra ${intent.extras?.keySet()?.joinToString(",") { "$it -> ${intent.extras?.get(it)}" }}")
        when {
            intent.data != null -> intent.data!! to intent.data!!.lastPathSegment
            intent.hasExtra(Intent.EXTRA_STREAM) -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) to intent.getStringExtra(Intent.EXTRA_TITLE)
            else -> null
        }?.let { (uri, possibleTitle) ->
            uri to (possibleTitle ?: uri.lastPathSegment)
        }?.let { (receivedUri, title) ->
            Log.d(LOGCAT_TAG, "Resolved Intent's URI $receivedUri, scheme: ${receivedUri.scheme}, title: $title")

            viewModelJob.cancel()

            viewModelJob = uiScope.launch(Dispatchers.Main.immediate) {
                userNotification.showOperationText(context.getString(R.string.requesting_android_permission))

                val proxyPermissions = ProxyPermissions()
                userNotification.askForAndroidPermission(proxyPermissions)
                val hasPermissions = withContext(Dispatchers.Default) { proxyPermissions.waitForResponse() }

                if (hasPermissions) {
                    userNotification.showOperationText(context.getString(R.string.loading_file_to_local_storage))

                    val fileUri = withContext(Dispatchers.Default) {
                        when (receivedUri.scheme) {
                            null -> Uri.fromParts("file", receivedUri.path, "")//going to assume this is a local file
                            "file" -> receivedUri//files can just be read
                            "http", "https" -> proxyWebUriToLocalFileUri(receivedUri, title)
                            "content" -> proxyContentUriToLocalFileUri(context, receivedUri, title)
                            else -> receivedUri //I don't know...
                        }
                    }

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
            viewModelJob.invokeOnCompletion { ex ->
                ex?.run {
                    printStackTrace()
                    userNotification.showOperationText(context.getString(R.string.error_failed_to_perform_proxy, this))
                }
            }
        }
                ?: userNotification.showOperationText(context.getString(R.string.error_intent_data_unrecognized))
    }

    fun destroy() {
        viewModelJob.cancel()
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

private fun proxyWebUriToLocalFileUri(uriToProxyLocally: Uri, title: String): Uri {
    Log.d(LOGCAT_TAG, "proxying web $uriToProxyLocally locally...")
    return inputStreamToLocalFileUri(
            URL(uriToProxyLocally.toString()).openStream(), title)
}

private fun proxyContentUriToLocalFileUri(context: Context, uriToProxyLocally: Uri, title: String): Uri {
    Log.d(LOGCAT_TAG, "proxying content $uriToProxyLocally locally...")
    return context.contentResolver.openInputStream(uriToProxyLocally)?.let {
        inputStreamToLocalFileUri(it, title)
    }
            ?: throw IllegalArgumentException("The URI $uriToProxyLocally could not be opened for reading!")
}

private fun inputStreamToLocalFileUri(receivedInputStream: InputStream, targetFileName: String): Uri {
    receivedInputStream.use { _ ->
        File(Environment.getExternalStorageDirectory(), "PebbleTransfer").run {
            if ((exists() && isDirectory) || mkdirs()) {
                val targetFile = File(this, targetFileName)
                targetFile.outputStream().use {
                    receivedInputStream.copyTo(it)
                }

                Log.d(LOGCAT_TAG, "Copied remote data to ${targetFile.absolutePath}")
                return Uri.fromFile(targetFile)
            } else {
                throw IOException("Was not able to create PebbleTransfer folder!")
            }
        }
    }
}
