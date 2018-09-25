package net.evendanan.robotic_pebble

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import kotlinx.android.synthetic.main.sub_main_auth_to_rebble_io.*
import kotlinx.android.synthetic.main.sub_main_boot_rebble_io.*
import kotlinx.android.synthetic.main.sub_main_final_steps.*
import kotlinx.android.synthetic.main.sub_main_install_pebble_app.*
import kotlinx.android.synthetic.main.web_fragment.*
import net.evendanan.chauffeur.lib.experiences.TransitionExperiences
import net.evendanan.chauffeur.lib.permissions.PermissionsFragmentChauffeurActivity


class MainActivity : PermissionsFragmentChauffeurActivity() {
    override fun getFragmentRootUiElementId() = R.id.root_content

    override fun createRootFragmentInstance() = MainFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

    val rebbleBootNeeded = rebbleBootDoneInApp?.not() ?: false
    val rebbleBootDone = rebbleBootDoneInApp ?: false

    val rebbleFinalSteps = pebbleAppInstalled

    companion object {
        fun calculateState(context: Context): UiVisibilityState {
            return if (!isPackageInstalled(PEBBLE_APP_PACKAGE, context.packageManager)) {
                NeedsPebbleApp
            } else {
                NeedAuth
            }
        }
    }

    object NeedsPebbleApp : UiVisibilityState(false)
    //this object will need to change once we are controlling the WebView.
    object NeedAuth : UiVisibilityState(true, false, false)
}

class MainFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.main_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        install_pebble_app.setOnClickListener { launchStorePage(activity!!, PEBBLE_APP_PACKAGE) }
        start_rebble_io_auth.setOnClickListener { launchWebPage(chauffeurActivity!!, "https://auth.rebble.io/") }
        start_rebble_io_boot.setOnClickListener { launchWebPage(chauffeurActivity!!, "https://boot.rebble.io/") }
        start_rtl_support.setOnClickListener { launchWebPage(chauffeurActivity!!, "https://elbbep.cpfx.ca/") }
    }

    private fun launchWebPage(activity: MainActivity, url: String) {
        activity.addFragmentToUi(WebFragment.createForUrl(url), TransitionExperiences.DEEPER_EXPERIENCE_TRANSITION)
    }

    private val chauffeurActivity: MainActivity?
        get() = activity as MainActivity?

    override fun onStart() {
        super.onStart()

        context?.run {
            UiVisibilityState.calculateState(this).run {
                pebble_app_installed.showInMainFragment(pebbleAppInstalled)
                pebble_app_not_installed.showInMainFragment(pebbleAppNotInstalled)
                rebble_auth_done.showInMainFragment(rebbleAuthDone)
                rebble_auth_needed.showInMainFragment(rebbleAuthNeeded)
                rebble_boot_done.showInMainFragment(rebbleBootDone)
                rebble_boot_needed.showInMainFragment(rebbleBootNeeded)
                rebble_setup_final.showInMainFragment(rebbleFinalSteps)
            }
        }
    }
}

class WebFragment : Fragment() {
    companion object {
        private const val EXTRA_URL = "WebFragment.EXTRA_URL"

        fun createForUrl(url: String): WebFragment = WebFragment().apply {
            this.arguments = Bundle().apply {
                putString(EXTRA_URL, url)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.web_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(EXTRA_URL)?.run {
            webView.settings.javaScriptEnabled = true
            //Some OAuth providers - namely, Google - will not allow the native WebView to
            //perform authentication. So, we fake it.
            //we should also provide an option to the user to perform the operation
            //with the on-device web-browser, in case they do not trust this WebView.
            webView.settings.userAgentString = "Mozilla/5.0 Google"
            webView.webViewClient = PebbleFilesWebViewClient(loadingIndicator)
            webView.loadUrl(this)
        }
    }
}

private class PebbleFilesWebViewClient(private val loadingIndicator: ProgressBar) : WebViewClient() {
    private val pebbleFileRegex = Regex(".*\\.pb[zlw]$")

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return request?.url?.let {
            if (pebbleFileRegex.matches(it.toString())) {
                askFileReceiverToDoIt(it, view?.context)
                loadingIndicator.visibility = View.GONE
                true
            } else {
                false//load in this web-view
            }
        } ?: false//load in this web-view
    }

    private fun askFileReceiverToDoIt(uriToDownload: Uri, context: Context?): Boolean {
        context?.startActivity(Intent(Intent.ACTION_VIEW, uriToDownload, context, FileReceiverActivity::class.java))

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
