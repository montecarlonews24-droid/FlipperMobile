package com.flippermobile

import android.Manifest
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.flippermobile.bt.BluetoothFragment
import com.flippermobile.nfc.NfcFragment
import com.flippermobile.wifi.WifiFragment

class MainActivity : AppCompatActivity() {
    private lateinit var tabNfc: Button
    private lateinit var tabWifi: Button
    private lateinit var tabBt: Button
    private var nfcFragment: NfcFragment? = null
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tabNfc = findViewById(R.id.tabNfc)
        tabWifi = findViewById(R.id.tabWifi)
        tabBt = findViewById(R.id.tabBt)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        requestPermissions()
        setupTabs()
        showFragment(NfcFragment().also { nfcFragment = it }, "NFC")
    }

    private fun setupTabs() {
        tabNfc.setOnClickListener {
            showFragment(NfcFragment().also { nfcFragment = it }, "NFC")
            updateTabColors("NFC")
        }
        tabWifi.setOnClickListener {
            showFragment(WifiFragment(), "WIFI")
            updateTabColors("WIFI")
        }
        tabBt.setOnClickListener {
            showFragment(BluetoothFragment(), "BT")
            updateTabColors("BT")
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.contentFrame, fragment, tag).commit()
    }

    private fun updateTabColors(active: String) {
        val orange = ContextCompat.getColor(this, R.color.orange)
        val cyan = ContextCompat.getColor(this, R.color.cyan)
        val green = ContextCompat.getColor(this, R.color.green)
        val dim = ContextCompat.getColor(this, R.color.dim)
        tabNfc.setTextColor(if (active == "NFC") orange else dim)
        tabWifi.setTextColor(if (active == "WIFI") green else dim)
        tabBt.setTextColor(if (active == "BT") cyan else dim)
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    override fun onResume() {
        super.onResume()
        val pi = android.app.PendingIntent.getActivity(
            this, 0,
            android.content.Intent(this, javaClass).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pi, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        tag?.let {
            val fragment = supportFragmentManager.findFragmentByTag("NFC") as? NfcFragment
            fragment?.onTagDiscovered(it)
        }
    }
}