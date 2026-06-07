package com.flippermobile.bt

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flippermobile.R

data class BtDevice(val name: String, val mac: String, val rssi: Int, val type: String)

class BluetoothFragment : Fragment() {
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var rvBt: RecyclerView
    private lateinit var tvLog: TextView
    private lateinit var btTotal: TextView
    private lateinit var btBle: TextView
    private lateinit var btClassic: TextView
    private var btAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private val devices = mutableListOf<BtDevice>()
    private val seenMacs = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            val mac = result.device.address
            if (seenMacs.contains(mac)) return
            seenMacs.add(mac)
            val name = try { result.device.name ?: "[BLE بدون اسم]" } catch (e: Exception) { "[BLE]" }
            devices.add(BtDevice(name, mac, result.rssi, "BLE"))
            updateUI(); addLog("[+] BLE: $name | $mac | ${result.rssi}dBm")
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            val mac = device.address
            if (seenMacs.contains(mac)) return
            seenMacs.add(mac)
            val name = try { device.name ?: "[Classic]" } catch (e: Exception) { "[Classic]" }
            devices.add(BtDevice(name, mac, rssi, "Classic"))
            updateUI(); addLog("[+] Classic: $name | $mac | ${rssi}dBm")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_bt, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScan = view.findViewById(R.id.btnScanBt)
        progressBar = view.findViewById(R.id.btProgress)
        rvBt = view.findViewById(R.id.rvBt)
        tvLog = view.findViewById(R.id.btLog)
        btTotal = view.findViewById(R.id.btTotal)
        btBle = view.findViewById(R.id.btBle)
        btClassic = view.findViewById(R.id.btClassic)
        val btManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager.adapter
        rvBt.layoutManager = LinearLayoutManager(context)
        btnScan.setOnClickListener { startScan() }
        addLog("BLUETOOTH SCANNER v1.0
───────────────────────────────
$ جاهز...")
    }

    override fun onResume() { super.onResume(); requireContext().registerReceiver(classicReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND)) }
    override fun onPause() { super.onPause(); stopScan(); try { requireContext().unregisterReceiver(classicReceiver) } catch (e: Exception) {} }

    private fun startScan() {
        if (isScanning) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "يحتاج صلاحية Bluetooth", Toast.LENGTH_SHORT).show(); return
        }
        if (btAdapter?.isEnabled == false) { Toast.makeText(context, "فعّل Bluetooth أولاً", Toast.LENGTH_SHORT).show(); return }
        isScanning = true; devices.clear(); seenMacs.clear()
        progressBar.visibility = View.VISIBLE
        btnScan.text = "⏳ جاري المسح..."; btnScan.isEnabled = false
        rvBt.adapter = null
        addLog("$ bt_scan --mode=ble+classic")
        try { btAdapter?.bluetoothLeScanner?.startScan(bleScanCallback); addLog("[*] BLE scan بدأ...") } catch (e: Exception) { addLog("[!] BLE: ${e.message}") }
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
                btAdapter?.startDiscovery(); addLog("[*] Classic scan بدأ...")
        } catch (e: Exception) { addLog("[!] Classic: ${e.message}") }
        handler.postDelayed({ stopScan() }, 12000)
    }

    private fun stopScan() {
        if (!isScanning) return; isScanning = false
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                btAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback); btAdapter?.cancelDiscovery()
            }
        } catch (e: Exception) {}
        progressBar.visibility = View.GONE
        btnScan.text = "🔄 مسح مجدد"; btnScan.isEnabled = true
        addLog("[✓] مسح مكتمل: ${devices.size} جهاز")
    }

    private fun updateUI() {
        btTotal.text = devices.size.toString()
        btBle.text = devices.count { it.type == "BLE" }.toString()
        btClassic.text = devices.count { it.type == "Classic" }.toString()
        rvBt.adapter = BtAdapter(devices.toList())
    }

    private fun addLog(text: String) { tvLog.text = "${tvLog.text}
$text" }
}

class BtAdapter(private val items: List<BtDevice>) : RecyclerView.Adapter<BtAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.btIcon)
        val name: TextView = view.findViewById(R.id.btName)
        val detail: TextView = view.findViewById(R.id.btDetail)
        val type: TextView = view.findViewById(R.id.btType)
        val signal: TextView = view.findViewById(R.id.btSignal)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_bt, parent, false))
    override fun onBindViewHolder(h: VH, i: Int) {
        val d = items[i]
        h.icon.text = if (d.type == "BLE") "📡" else "🔵"
        h.name.text = d.name; h.detail.text = "${d.mac} | ${d.rssi}dBm"; h.type.text = d.type
        h.type.setTextColor(if (d.type == "BLE") 0xFF00D4FF.toInt() else 0xFFFF6B00.toInt())
        h.signal.text = when { d.rssi > -55 -> "████"; d.rssi > -65 -> "███░"; d.rssi > -75 -> "██░░"; else -> "█░░░" }
    }
    override fun getItemCount() = items.size
}