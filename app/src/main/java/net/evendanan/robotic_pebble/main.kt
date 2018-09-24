package net.evendanan.robotic_pebble

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.sub_main_auth_to_rebble_io.*
import kotlinx.android.synthetic.main.sub_main_boot_rebble_io.*
import kotlinx.android.synthetic.main.sub_main_final_steps.*
import kotlinx.android.synthetic.main.sub_main_install_pebble_app.*
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
        start_rebble_io_auth.setOnClickListener { launchWebPage(activity!!, "https://auth.rebble.io/") }
        start_rebble_io_boot.setOnClickListener { launchWebPage(activity!!, "https://boot.rebble.io/") }
        start_rtl_support.setOnClickListener { launchWebPage(activity!!, "https://elbbep.cpfx.ca/") }
    }

    private fun launchWebPage(activity: Activity, url: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

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