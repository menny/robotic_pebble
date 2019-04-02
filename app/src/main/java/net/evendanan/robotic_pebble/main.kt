package net.evendanan.robotic_pebble

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import net.evendanan.chauffeur.lib.experiences.TransitionExperiences
import net.evendanan.chauffeur.lib.permissions.PermissionsFragmentChauffeurActivity

private const val MAIN_TAG = "RebblePebble"

class MainActivity : PermissionsFragmentChauffeurActivity() {

    internal var authSuccess = false
    internal var bootSuccess = false

    override fun getFragmentRootUiElementId() = R.id.root_content

    override fun createRootFragmentInstance() = MainFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            // Restore value of members from saved state
            authSuccess = it.getBoolean(STATE_AUTH)
            bootSuccess = it.getBoolean(STATE_BOOT)
        }
        setContentView(R.layout.activity_main)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        // Save the user's current game state
        outState?.run {
            putBoolean(STATE_AUTH, authSuccess)
            putBoolean(STATE_BOOT, bootSuccess)
        }

        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_AUTH = "net.evendanan.robotic_pebble.MainActivity.STATE_AUTH"
        private const val STATE_BOOT = "net.evendanan.robotic_pebble.MainActivity.STATE_BOOT"
    }
}

private fun View.showInMainFragment(shown: Boolean) {
    visibility = when (shown) {
        true -> View.VISIBLE
        else -> View.GONE
    }
}

private sealed class UiVisibilityState(val pebbleAppInstalled: Boolean, rebbleAuthDoneInApp: Boolean? = null, rebbleBootDoneInApp: Boolean? = null) {
    val pebbleAppNotInstalled = !pebbleAppInstalled

    val rebbleAuthNeeded = rebbleAuthDoneInApp?.not() ?: false
    val rebbleAuthDone = rebbleAuthDoneInApp ?: false

    val rebbleBootNeeded = rebbleAuthDone && rebbleBootDoneInApp?.not() ?: false
    val rebbleBootDone = rebbleAuthDone && rebbleBootDoneInApp ?: false

    val rebbleFinalSteps = rebbleAuthDone && rebbleBootDone && pebbleAppInstalled

    companion object {
        fun calculateState(context: MainActivity): UiVisibilityState {
            return context.run {
                when {
                    !isPackageInstalled(PEBBLE_APP_PACKAGE, packageManager) -> NeedsPebbleApp
                    !authSuccess -> NeedsAuth
                    !bootSuccess -> NeedsBoot
                    else -> AllReady
                }
            }
        }
    }

    object NeedsPebbleApp : UiVisibilityState(false)
    object NeedsAuth : UiVisibilityState(true, false)
    object NeedsBoot : UiVisibilityState(true, true, false)
    object AllReady : UiVisibilityState(true, true, true)
}

class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.main_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.install_pebble_app).setOnClickListener { launchStorePage(activity!!, PEBBLE_APP_PACKAGE) }
        view.findViewById<View>(R.id.start_rebble_io_auth).setOnClickListener { launchWebPage(chauffeurActivity!!, WebFragment.AUTH_URL) }
        view.findViewById<View>(R.id.start_rebble_io_boot).setOnClickListener { launchWebPage(chauffeurActivity!!, WebFragment.BOOT_URL) }
        view.findViewById<View>(R.id.start_rtl_support).setOnClickListener { launchWebPage(chauffeurActivity!!, "https://elbbep.cpfx.ca/") }
        view.findViewById<View>(R.id.manage_account).setOnClickListener { launchWebPage(chauffeurActivity!!, "${WebFragment.AUTH_URL}account/") }
    }

    private fun launchWebPage(activity: MainActivity, url: String) {
        activity.addFragmentToUi(WebFragment.createForUrl(url), TransitionExperiences.DEEPER_EXPERIENCE_TRANSITION)
    }

    private val chauffeurActivity: MainActivity?
        get() = activity as? MainActivity

    override fun onStart() {
        super.onStart()

        chauffeurActivity?.apply {
            UiVisibilityState.calculateState(this).apply {
                view?.apply {
                    findViewById<View>(R.id.pebble_app_installed).showInMainFragment(pebbleAppInstalled)
                    findViewById<View>(R.id.pebble_app_not_installed).showInMainFragment(pebbleAppNotInstalled)
                    findViewById<View>(R.id.rebble_auth_done).showInMainFragment(rebbleAuthDone)
                    findViewById<View>(R.id.rebble_auth_needed).showInMainFragment(rebbleAuthNeeded)
                    findViewById<View>(R.id.rebble_boot_done).showInMainFragment(rebbleBootDone)
                    findViewById<View>(R.id.rebble_boot_needed).showInMainFragment(rebbleBootNeeded)
                    findViewById<View>(R.id.rebble_setup_final).showInMainFragment(rebbleFinalSteps)
                }
            }
        }
    }
}

internal class SuccessPageFinder(private val webView: WebView, private val activity: MainActivity) {

    init {
        webView.setFindListener { _, numberOfMatches, _ ->
            if (numberOfMatches > 0) {
                when {
                    webView.url.startsWith(WebFragment.AUTH_URL, true) -> {
                        activity.authSuccess = true
                        activity.returnToRootFragment(false)
                    }
                }
            }
        }

        webView.findAllAsync("You're all set!")
    }
}

class WebFragment : Fragment() {
    companion object {
        private const val EXTRA_URL = "WebFragment.EXTRA_URL"
        internal const val AUTH_URL = "https://auth.rebble.io/"
        internal const val BOOT_URL = "https://boot.rebble.io/"

        fun createForUrl(url: String): WebFragment = WebFragment().apply {
            this.arguments = Bundle().apply {
                putString(EXTRA_URL, url)
            }
        }
    }

    private val chauffeurActivity: MainActivity?
        get() = activity as? MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.web_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chauffeurActivity?.also { mainActivity ->
            arguments?.getString(EXTRA_URL)?.let { url ->
                view.findViewById<WebView>(R.id.webView)?.apply {
                    settings.javaScriptEnabled = true
                    //Some OAuth providers - namely, Google - will not allow the native WebView to
                    //perform authentication. So, we fake it.
                    //we should also provide an option to the user to perform the operation
                    //with the on-device web-browser, in case they do not trust this WebView.
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.DISPLAY}) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19"
                    Log.d(MAIN_TAG, "Using user-agent '${settings.userAgentString}'")
                    webViewClient = PebbleFilesWebViewClient(view.findViewById(R.id.loadingIndicator), mainActivity)
                    loadUrl(url)
                    SuccessPageFinder(this, mainActivity)
                }
            }
        }
    }

    private class PebbleFilesWebViewClient(private val loadingIndicator: ProgressBar, private val activity: MainActivity) : WebViewClient() {
        private val pebbleFileRegex = Regex(".*\\.pb[zlw]$")
        private val pebbleRequestRegex = Regex("^pebble://.+")

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return request?.url.let {
                when {
                    it == null -> false //shouldn't happen, but let's be careful
                    pebbleFileRegex.matches(it.toString()) -> {
                        it.askFileReceiverToDoIt(activity)
                        loadingIndicator.visibility = View.GONE
                        true
                    }
                    pebbleRequestRegex.matches(it.toString()) -> {
                        activity.bootSuccess = true
                        activity.startActivity(Intent(Intent.ACTION_VIEW, it))
                        loadingIndicator.visibility = View.GONE
                        activity.returnToRootFragment(false)
                        true
                    }
                    else -> false
                }
            }
        }

        private fun Uri.askFileReceiverToDoIt(context: Context): Boolean {
            context.startActivity(Intent(Intent.ACTION_VIEW, this, context, FileReceiverActivity::class.java))
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            loadingIndicator.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            loadingIndicator.visibility = View.GONE
        }
    }
}
