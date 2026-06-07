package com.flippermobile.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flippermobile.R
import kotlinx.coroutines.*

data class NfcCard(val uid: String, val type: String, val proto: String, val tech: String, val rawData: String, val time: String)

class NfcFragment : Fragment() {
    private lateinit var tvLog: TextView
    private lateinit var cardResult: View
    private lateinit var tvUid: TextView
    private lateinit var tvType: TextView
    private lateinit var tvProto: TextView
    private lateinit var tvTech: TextView
    private lateinit var tvRaw: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCopy: Button
    private lateinit var rvSavedCards: RecyclerView
    private lateinit var countRead: TextView
    private lateinit var countSaved: TextView
    private lateinit var countType: TextView
    private var lastCard: NfcCard? = null
    private val savedCards = mutableListOf<NfcCard>()
    private var readCount = 0
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_nfc, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvLog = view.findViewById(R.id.tvLog)
        cardResult = view.findViewById(R.id.cardResult)
        tvUid = view.findViewById(R.id.tvUid)
        tvType = view.findViewById(R.id.tvType)
        tvProto = view.findViewById(R.id.tvProto)
        tvTech = view.findViewById(R.id.tvTech)
        tvRaw = view.findViewById(R.id.tvRaw)
        btnSave = view.findViewById(R.id.btnSave)
        btnCopy = view.findViewById(R.id.btnCopy)
        rvSavedCards = view.findViewById(R.id.rvSavedCards)
        countRead = view.findViewById(R.id.countRead)
        countSaved = view.findViewById(R.id.countSaved)
        countType = view.findViewById(R.id.countType)
        rvSavedCards.layoutManager = LinearLayoutManager(context)
        btnSave.setOnClickListener { saveCurrentCard() }
        btnCopy.setOnClickListener { copyUID() }
        addLog("FLIPPER_MOBILE NFC v1.0")
        addLog("───────────────────────────────")
        addLog("$ قرّب أي كارت NFC للقراءة...")
    }

    fun onTagDiscovered(tag: Tag) {
        scope.launch {
            withContext(Dispatchers.IO) { processTag(tag) }
        }
    }

    private suspend fun processTag(tag: Tag) {
        val uid = bytesToHex(tag.id)
        val techs = tag.techList.toList()
        withContext(Dispatchers.Main) {
            addLog("[+] تم اكتشاف كارت!")
            addLog("[*] UID: $uid")
        }
        val cardType = determineCardType(techs)
        val proto = determineProtocol(techs)
        val techName = techs.map { it.split(".").last() }.joinToString(", ")
        val rawData = withContext(Dispatchers.IO) { readRawData(tag, techs) }
        withContext(Dispatchers.Main) {
            addLog("[+] النوع: $cardType")
            addLog("[✓] قراءة ناجحة!")
            val card = NfcCard(uid, cardType, proto, techName, rawData,
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            lastCard = card
            readCount++
            countRead.text = readCount.toString()
            countType.text = cardType.split(" ").first()
            showCardResult(card)
        }
    }

    private fun readRawData(tag: Tag, techs: List<String>): String {
        return try {
            when {
                techs.any { it.contains("MifareClassic") } -> {
                    val m = MifareClassic.get(tag) ?: return "غير متاح"
                    m.connect()
                    val sb = StringBuilder()
                    for (s in 0 until minOf(m.sectorCount, 4)) {
                        try {
                            if (m.authenticateSectorWithKeyA(s, MifareClassic.KEY_DEFAULT))
                                sb.append("S$s:${bytesToHex(m.readBlock(m.sectorToBlock(s)))} ")
                            else sb.append("S$s:[LOCKED] ")
                        } catch (e: Exception) { sb.append("S$s:[ERR] ") }
                    }
                    m.close(); sb.toString().trim()
                }
                techs.any { it.contains("Ndef") } -> {
                    val n = Ndef.get(tag) ?: return "غير متاح"
                    n.connect()
                    val r = n.ndefMessage?.records?.firstOrNull()?.payload?.let { bytesToHex(it) } ?: "فارغ"
                    n.close(); "NDEF: $r"
                }
                techs.any { it.contains("NfcA") } -> {
                    val a = NfcA.get(tag) ?: return "غير متاح"
                    a.connect()
                    val r = "ATQA:${bytesToHex(a.atqa)} SAK:${a.sak.toString(16).uppercase()}"
                    a.close(); r
                }
                else -> "بروتوكول غير مدعوم"
            }
        } catch (e: Exception) { "خطأ: ${e.message}" }
    }

    private fun determineCardType(techs: List<String>) = when {
        techs.any { it.contains("MifareClassic") } -> "MIFARE Classic"
        techs.any { it.contains("MifareUltralight") } -> "MIFARE Ultralight"
        techs.any { it.contains("IsoDep") } -> "ISO-DEP"
        techs.any { it.contains("Ndef") } -> "NDEF Tag"
        techs.any { it.contains("NfcF") } -> "FeliCa"
        techs.any { it.contains("NfcV") } -> "ISO 15693"
        else -> "NFC-A"
    }

    private fun determineProtocol(techs: List<String>) = when {
        techs.any { it.contains("NfcF") } -> "ISO 18092"
        techs.any { it.contains("NfcV") } -> "ISO 15693"
        techs.any { it.contains("NfcB") } -> "ISO 14443-B"
        else -> "ISO 14443-A"
    }

    private fun showCardResult(card: NfcCard) {
        tvUid.text = card.uid; tvType.text = card.type; tvProto.text = card.proto
        tvTech.text = card.tech; tvRaw.text = card.rawData
        cardResult.visibility = View.VISIBLE
    }

    private fun saveCurrentCard() {
        val card = lastCard ?: return
        savedCards.add(card); nfcSaved++
        countSaved.text = savedCards.size.toString()
        rvSavedCards.adapter = SavedAdapter(savedCards)
        addLog("[💾] تم حفظ: ${card.uid}")
        Toast.makeText(context, "تم حفظ الكارت", Toast.LENGTH_SHORT).show()
    }

    private var nfcSaved = 0

    private fun copyUID() {
        val uid = lastCard?.uid ?: return
        val cm = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("NFC UID", uid))
        addLog("[📋] تم نسخ: $uid")
        Toast.makeText(context, "تم نسخ UID", Toast.LENGTH_SHORT).show()
    }

    private fun addLog(text: String) {
        val current = tvLog.text.toString()
        tvLog.text = "$current
$text"
    }

    private fun bytesToHex(bytes: ByteArray) = bytes.joinToString(":") { "%02X".format(it) }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class SavedAdapter(private val cards: List<NfcCard>) : RecyclerView.Adapter<SavedAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val t1: TextView = view.findViewById(android.R.id.text1)
        val t2: TextView = view.findViewById(android.R.id.text2)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
    override fun onBindViewHolder(h: VH, i: Int) {
        h.t1.text = "💳 ${cards[i].uid}"; h.t1.setTextColor(0xFFFF6B00.toInt())
        h.t2.text = "${cards[i].type} — ${cards[i].time}"; h.t2.setTextColor(0xFF555577.toInt())
    }
    override fun getItemCount() = cards.size
}