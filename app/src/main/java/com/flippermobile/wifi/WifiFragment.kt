package com.flippermobile.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flippermobile.R

class WifiFragment : Fragment() {
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rvWifi: RecyclerView
    private lateinit var tvLog: TextView
    private lateinit var wifiTotal: TextView
    private lateinit var wifiOpen: TextView
    private lateinit var wifiStrong: TextView
    private lateinit var wifiManager: WifiManager
    private var isScanning = false
    private val results = mutableListOf<ScanResult>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) onScanComplete()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_wifi, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScan = view.findViewById(R.id.btnScanWifi)
        progressBar = view.findViewById(R.id.wifiProgress)
        rvWifi = view.findViewById(R.id.rvWifi)
        tvLog = view.findViewById(R.id.wifiLog)
        wifiTotal = view.findViewById(R.id.wifiTotal)
        wifiOpen = view.findViewById(R.id.wifiOpen)
        wifiStrong = view.findViewById(R.id.wifiStrong)
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        rvWifi.layoutManager = LinearLayoutManager(context)
        btnScan.setOnClickListener { startScan() }
        addLog("WIFI SCANNER v1.0
───────────────────────────────
$ جاهز...")
    }

    override fun onResume() { super.onResume(); requireContext().registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) }
    override fun onPause() { super.onPause(); try { requireContext().unregisterReceiver(receiver) } catch (e: Exception) {} }

    private fun startScan() {
        if (isScanning) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "يحتاج صلاحية الموقع", Toast.LENGTH_SHORT).show(); return
        }
        isScanning = true; results.clear()
        progressBar.visibility = View.VISIBLE
        btnScan.text = "⏳ جاري المسح..."; btnScan.isEnabled = false
        rvWifi.adapter = null
        addLog("$ wifi_scan --all-channels")
        if (!wifiManager.startScan()) onScanComplete()
    }

    @Suppress("MissingPermission")
    private fun onScanComplete() {
        isScanning = false
        progressBar.visibility = View.GONE
        btnScan.text = "🔄 مسح مجدد"; btnScan.isEnabled = true
        val scan = try { wifiManager.scanResults ?: emptyList() } catch (e: Exception) { emptyList() }
        results.clear(); results.addAll(scan.sortedByDescending { it.level })
        val open = results.count { !it.capabilities.contains("WPA") && !it.capabilities.contains("WEP") }
        val strong = results.count { it.level > -60 }
        wifiTotal.text = results.size.toString(); wifiOpen.text = open.toString(); wifiStrong.text = strong.toString()
        addLog("[✓] تم اكتشاف ${results.size} شبكة")
        results.take(5).forEach { addLog("[+] ${if(it.SSID.isNullOrBlank()) "[مخفية]" else it.SSID} | ${getSec(it.capabilities)} | ${it.level}dBm") }
        rvWifi.adapter = WifiAdapter(results)
    }

    private fun getSec(cap: String) = when { cap.contains("WPA3") -> "WPA3"; cap.contains("WPA2") -> "WPA2"; cap.contains("WPA") -> "WPA"; cap.contains("WEP") -> "WEP"; else -> "OPEN" }
    private fun addLog(text: String) { tvLog.text = "${tvLog.text}
$text" }
}

class WifiAdapter(private val items: List<ScanResult>) : RecyclerView.Adapter<WifiAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.wifiIcon)
        val name: TextView = view.findViewById(R.id.wifiName)
        val detail: TextView = view.findViewById(R.id.wifiDetail)
        val security: TextView = view.findViewById(R.id.wifiSecurity)
        val signal: TextView = view.findViewById(R.id.wifiSignal)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false))
    override fun onBindViewHolder(h: VH, i: Int) {
        val r = items[i]
        val name = if (r.SSID.isNullOrBlank()) "[مخفية]" else r.SSID
        val sec = when { r.capabilities.contains("WPA3") -> "WPA3"; r.capabilities.contains("WPA2") -> "WPA2"; r.capabilities.contains("WPA") -> "WPA"; r.capabilities.contains("WEP") -> "WEP"; else -> "OPEN" }
        h.icon.text = if (r.SSID.isNullOrBlank()) "🔇" else "📶"
        h.name.text = name; h.detail.text = "${r.BSSID} | ${r.level}dBm"; h.security.text = sec
        h.signal.text = when { r.level > -55 -> "████"; r.level > -65 -> "███░"; r.level > -75 -> "██░░"; else -> "█░░░" }
    }
    override fun getItemCount() = items.size
}