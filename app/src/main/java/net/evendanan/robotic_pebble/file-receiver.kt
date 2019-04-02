package net.evendanan.robotic_pebble

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.upload_file.*
import net.evendanan.chauffeur.lib.permissions.PermissionsFragmentChauffeurActivity
import net.evendanan.chauffeur.lib.permissions.PermissionsRequest

class FileReceiverActivity : PermissionsFragmentChauffeurActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun getFragmentRootUiElementId() = R.id.root_content

    override fun createRootFragmentInstance() = FileProxyFragment()
}

class FileProxyFragment : Fragment(), UserNotification {
    private val fileProxy = FileProxy(this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.upload_file, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.let {
            fileProxy.handleIntent(it, it.intent)
            it.intent = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fileProxy.destroy()
    }

    override fun closeUi() {
        activity?.finish()
    }

    override fun showOperationText(text: CharSequence) {
        operation_text.text = text
    }

    private val chauffeurActivity: FileReceiverActivity?
        get() = activity as? FileReceiverActivity

    override fun askForAndroidPermission(request: PermissionsRequest) {
        chauffeurActivity?.startPermissionsRequest(request)
    }

    override fun showPebbleAppInstall(pebbleAppPackage: String) {
        install_from_play_store_button.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                activity?.run {
                    launchStorePage(this, pebbleAppPackage)
                    closeUi()
                }
            }
        }
    }
}
