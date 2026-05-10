package ch.zodi.pocxtracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val DEFAULT_EXPLORER_API = "https://explorer.bitcoin-pocx.org/api/"
private const val DEFAULT_ADDRESS = "pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv"
private const val SATS_PER_BTCX = 100_000_000.0
private const val APP_VERSION = "PoCX Address Tracker v1.0"

private val PhoenixBlue = Color(0xFF244F7C)
private val PhoenixDarkBlue = Color(0xFF173B61)
private val PhoenixBg = Color(0xFFEFF4F8)
private val PhoenixCard = Color(0xFFFFFFFF)
private val PhoenixGreen = Color(0xFF4DFFC3)

@Serializable data class AddressInfo(val address:String, val chain_stats:Stats, val mempool_stats:Stats)
@Serializable data class Stats(val funded_txo_count:Int=0,val funded_txo_sum:Long=0,val spent_txo_count:Int=0,val spent_txo_sum:Long=0,val tx_count:Int=0)
@Serializable data class Tx(val txid:String, val vin:List<Vin> = emptyList(), val vout:List<Vout> = emptyList(), val status:TxStatus)
@Serializable data class Vin(val is_coinbase:Boolean=false, val prevout:Prevout?=null)
@Serializable data class Prevout(val scriptpubkey_address:String?=null, val value:Long=0)
@Serializable data class Vout(val scriptpubkey_address:String?=null, val value:Long=0)
@Serializable data class TxStatus(val confirmed:Boolean=false, val block_height:Long?=null, val block_hash:String?=null, val block_time:Long?=null)

data class AddressEntry(
    val address:String,
    val polling:Boolean = true,
    val label:String = defaultAddressLabel(address)
)

data class MiningBlock(val txid:String, val height:Long, val time:Long, val reward:Long)
data class PocxTransaction(val txid:String, val time:Long, val amount:Long, val height:Long?, val confirmed:Boolean)

data class UiState(
    val loading:Boolean=true,
    val error:String?=null,
    val balance:Long=0,
    val txCount:Int=0,
    val currentHeight:Long?=null,
    val won:List<MiningBlock> = emptyList(),
    val incoming:List<PocxTransaction> = emptyList(),
    val outgoing:List<PocxTransaction> = emptyList()
)

enum class Lang { DE, EN }

class Texts(private val lang: Lang) {
    fun appTitle() = "PoCX Address Tracker"
    fun currentHeight(h:Long) = if (lang == Lang.DE) "Aktuelle Blockhöhe: $h" else "Current block height: $h"
    fun settings() = if (lang == Lang.DE) "Einstellungen" else "Settings"
    fun explorerApi() = if (lang == Lang.DE) "PoCX-Explorer API" else "PoCX Explorer API"
    fun globalPush() = if (lang == Lang.DE) "Push-Benachrichtigungen global aktiv" else "Global push notifications enabled"
    fun testNotification() = if (lang == Lang.DE) "Test-Benachrichtigung senden" else "Send test notification"
    fun save() = if (lang == Lang.DE) "Speichern" else "Save"
    fun cancel() = if (lang == Lang.DE) "Abbrechen" else "Cancel"
    fun addressLabel() = if (lang == Lang.DE) "POCX-Adresse" else "POCX address"
    fun label() = if (lang == Lang.DE) "Label" else "Label"
    fun saveRefresh() = if (lang == Lang.DE) "Adresse speichern / aktualisieren" else "Save address / refresh"
    fun savedAddresses() = if (lang == Lang.DE) "Gespeicherte Adressen" else "Saved addresses"
    fun notifyBlocks() = if (lang == Lang.DE) "Blockmeldungen" else "Block notifications"
    fun delete() = if (lang == Lang.DE) "Löschen" else "Delete"
    fun select() = if (lang == Lang.DE) "Anzeigen" else "Show"
    fun active() = if (lang == Lang.DE) "Aktiv" else "Active"
    fun balance() = "Balance"
    fun blocks() = if (lang == Lang.DE) "Blöcke" else "Blocks"
    fun won() = if (lang == Lang.DE) "gewonnen" else "won"
    fun incoming() = if (lang == Lang.DE) "Eingehend" else "Incoming"
    fun outgoing() = if (lang == Lang.DE) "Ausgehend" else "Outgoing"
    fun tx() = "TX"
    fun stats() = if (lang == Lang.DE) "Statistik" else "Stats"
    fun totalRewards() = if (lang == Lang.DE) "Rewards gesamt" else "Total rewards"
    fun avgReward() = if (lang == Lang.DE) "Ø Reward" else "Avg reward"
    fun latestBlock() = if (lang == Lang.DE) "Letzter Gewinn" else "Latest win"
    fun avgBlocksPerDay() = if (lang == Lang.DE) "Ø Blöcke / Tag" else "Avg blocks / day"
    fun txVolumeIn() = if (lang == Lang.DE) "Eingang gesamt" else "Total incoming"
    fun txVolumeOut() = if (lang == Lang.DE) "Ausgang gesamt" else "Total outgoing"
    fun wonBlocks(n:Int) = if (lang == Lang.DE) "Gewonnene Blöcke ($n)" else "Won blocks ($n)"
    fun incomingTx(n:Int) = if (lang == Lang.DE) "Eingehende Transaktionen ($n)" else "Incoming transactions ($n)"
    fun outgoingTx(n:Int) = if (lang == Lang.DE) "Ausgehende Transaktionen ($n)" else "Outgoing transactions ($n)"
    fun previous() = if (lang == Lang.DE) "Zurück" else "Previous"
    fun next() = if (lang == Lang.DE) "Weiter" else "Next"
    fun page(a:Int, b:Int) = if (lang == Lang.DE) "Seite $a / $b" else "Page $a / $b"
    fun noEntries() = if (lang == Lang.DE) "Keine Einträge" else "No entries"
    fun blockHeight() = if (lang == Lang.DE) "Blockhöhe" else "Block height"
    fun reward() = "Reward"
    fun time() = if (lang == Lang.DE) "Zeit" else "Time"
    fun amount() = if (lang == Lang.DE) "Menge" else "Amount"
    fun status() = "Status"
    fun confirmed() = if (lang == Lang.DE) "Bestätigt" else "Confirmed"
    fun unconfirmed() = if (lang == Lang.DE) "Unbestätigt" else "Unconfirmed"
    fun input() = if (lang == Lang.DE) "Eingang" else "Incoming"
    fun output() = if (lang == Lang.DE) "Ausgang" else "Outgoing"
    fun errorPrefix() = if (lang == Lang.DE) "Fehler" else "Error"
    fun addressNotFound() = if (lang == Lang.DE) "Adresse nicht gefunden oder ungültig." else "Address not found or invalid."
}

interface EsploraApi {
    @GET("address/{address}") suspend fun address(@Path("address") address:String): AddressInfo
    @GET("address/{address}/txs") suspend fun txs(@Path("address") address:String): List<Tx>
    @GET("address/{address}/txs/chain/{last}") suspend fun txsAfter(@Path("address") address:String, @Path("last") last:String): List<Tx>
    @GET("blocks/tip/height") suspend fun tipHeight(): Long
}

fun createApi(baseUrl:String): EsploraApi {
    val json = Json { ignoreUnknownKeys = true }
    return Retrofit.Builder()
        .baseUrl(normalizeApiUrl(baseUrl))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(EsploraApi::class.java)
}

class MainActivity: ComponentActivity() {
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ensureNotificationChannel(this)
        scheduleWorker(this)

        setContent { App(this) }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun App(ctx: Context) {
    val prefs = ctx.getSharedPreferences("pocx", Context.MODE_PRIVATE)

    var lang by remember { mutableStateOf(if (prefs.getString("lang", "DE") == "EN") Lang.EN else Lang.DE) }
    val t = Texts(lang)

    var explorerApi by remember { mutableStateOf(loadExplorerApi(prefs)) }
    var globalPushEnabled by remember { mutableStateOf(loadGlobalPushEnabled(prefs)) }
    var showSettings by remember { mutableStateOf(false) }

    var entries by remember { mutableStateOf(loadAddressEntries(prefs)) }
    var address by remember { mutableStateOf(prefs.getString("activeAddress", entries.firstOrNull()?.address ?: DEFAULT_ADDRESS) ?: DEFAULT_ADDRESS) }
    var inputAddress by remember { mutableStateOf(address) }
    var state by remember { mutableStateOf(UiState()) }
    var refreshTick by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    fun persistEntries(newEntries: List<AddressEntry>) {
        entries = newEntries.distinctBy { it.address }
        saveAddressEntries(prefs, entries)
    }

    fun refreshAndCheckNotifications() {
        refreshTick++
        scope.launch {
            checkForNewBlocksForEnabledAddresses(ctx, prefs, explorerApi, lang, notifyOnFirst = false)
        }
    }

    LaunchedEffect(address, lang, refreshTick, explorerApi) {
        state = UiState(loading = true)
        state = loadState(address, t, explorerApi)
        checkForNewBlocksForEnabledAddresses(ctx, prefs, explorerApi, lang, notifyOnFirst = false)
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = { refreshAndCheckNotifications() }
    )

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(PhoenixBg)
                .pullRefresh(pullRefreshState)
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(PhoenixDarkBlue)
                        .padding(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showSettings = true },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.width(34.dp)
                            ) {
                                Text("⋮", color = PhoenixGreen, style = MaterialTheme.typography.headlineSmall)
                            }

                            Column {
                                Text(t.appTitle(), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                state.currentHeight?.let {
                                    Text(t.currentHeight(it), color = PhoenixGreen, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        TextButton(onClick = {
                            lang = if (lang == Lang.DE) Lang.EN else Lang.DE
                            prefs.edit().putString("lang", lang.name).apply()
                        }) {
                            Text(if (lang == Lang.DE) "🇬🇧" else "🇩🇪", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }

                LazyColumn(
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    item {
                        OutlinedTextField(
                            value = inputAddress,
                            onValueChange = { inputAddress = it.trim() },
                            label = { Text(t.addressLabel()) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val clean = inputAddress.trim()
                                if (clean.isNotEmpty()) {
                                    scope.launch {
                                        state = UiState(loading = true)
                                        val newState = loadState(clean, t, explorerApi)

                                        if (newState.error != null) {
                                            state = newState
                                        } else {
                                            address = clean
                                            state = newState

                                            prefs.edit()
                                                .putString("activeAddress", clean)
                                                .putString("address", clean)
                                                .apply()

                                            if (entries.none { it.address == clean }) {
                                                persistEntries(entries + AddressEntry(clean, true, defaultAddressLabel(clean)))
                                            }

                                            checkForNewBlocksForEnabledAddresses(ctx, prefs, explorerApi, lang, notifyOnFirst = false)
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PhoenixBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(t.saveRefresh())
                        }

                        Spacer(Modifier.height(12.dp))

                        AddressBook(
                            entries = entries,
                            activeAddress = address,
                            t = t,
                            onSelect = { selected ->
                                address = selected
                                inputAddress = selected
                                prefs.edit()
                                    .putString("activeAddress", selected)
                                    .putString("address", selected)
                                    .apply()
                                refreshAndCheckNotifications()
                            },
                            onTogglePolling = { selected, enabled ->
                                persistEntries(entries.map {
                                    if (it.address == selected) it.copy(polling = enabled) else it
                                })
                                scope.launch {
                                    checkForNewBlocksForEnabledAddresses(ctx, prefs, explorerApi, lang, notifyOnFirst = false)
                                }
                            },
                            onLabelChange = { selected, label ->
                                persistEntries(entries.map {
                                    if (it.address == selected) it.copy(label = label) else it
                                })
                            },
                            onDelete = { selected ->
                                val newEntries = entries.filterNot { it.address == selected }
                                persistEntries(newEntries)

                                if (address == selected) {
                                    val next = newEntries.firstOrNull()?.address ?: DEFAULT_ADDRESS
                                    address = next
                                    inputAddress = next
                                    prefs.edit()
                                        .putString("activeAddress", next)
                                        .putString("address", next)
                                        .apply()
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        when {
                            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.error != null -> Text("${t.errorPrefix()}: ${state.error}", color = Color.Red)
                            else -> Dashboard(state, t, explorerApi)
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (showSettings) {
                SettingsDialog(
                    t = t,
                    currentExplorerApi = explorerApi,
                    globalPushEnabled = globalPushEnabled,
                    onDismiss = { showSettings = false },
                    onTestNotification = {
                        if (globalPushEnabled) sendTestNotification(ctx, lang)
                    },
                    onGlobalPushChanged = {
                        globalPushEnabled = it
                        saveGlobalPushEnabled(prefs, it)
                    },
                    onSave = { newUrl ->
                        explorerApi = normalizeApiUrl(newUrl)
                        saveExplorerApi(prefs, explorerApi)
                        showSettings = false
                        refreshAndCheckNotifications()
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    t:Texts,
    currentExplorerApi:String,
    globalPushEnabled:Boolean,
    onDismiss:()->Unit,
    onTestNotification:()->Unit,
    onGlobalPushChanged:(Boolean)->Unit,
    onSave:(String)->Unit
) {
    var value by remember { mutableStateOf(currentExplorerApi) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.settings()) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    label = { Text(t.explorerApi()) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t.globalPush(), modifier = Modifier.weight(1f))
                    Switch(
                        checked = globalPushEnabled,
                        onCheckedChange = onGlobalPushChanged
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onTestNotification,
                    enabled = globalPushEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t.testNotification())
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    APP_VERSION,
                    color = PhoenixBlue,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value) }) {
                Text(t.save())
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(t.cancel())
            }
        }
    )
}

@Composable
fun AddressBook(
    entries:List<AddressEntry>,
    activeAddress:String,
    t:Texts,
    onSelect:(String)->Unit,
    onTogglePolling:(String, Boolean)->Unit,
    onLabelChange:(String, String)->Unit,
    onDelete:(String)->Unit
) {
    var open by remember { mutableStateOf(false) }

    Button(
        onClick = { open = !open },
        colors = ButtonDefaults.buttonColors(containerColor = PhoenixDarkBlue),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(if (open) "▼ ${t.savedAddresses()}" else "▶ ${t.savedAddresses()}")
    }

    if (open) {
        if (entries.isEmpty()) {
            Text(t.noEntries(), Modifier.padding(8.dp))
        }

        entries.forEach { entry ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = PhoenixCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    OutlinedTextField(
                        value = entry.label,
                        onValueChange = { onLabelChange(entry.address, it) },
                        label = { Text(t.label()) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(entry.address, style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(
                            onClick = {
                                onSelect(entry.address)
                                open = false
                            },
                            border = if (entry.address == activeAddress)
                                BorderStroke(2.dp, PhoenixGreen)
                            else
                                ButtonDefaults.outlinedButtonBorder,
                            colors = if (entry.address == activeAddress)
                                ButtonDefaults.outlinedButtonColors(contentColor = PhoenixGreen)
                            else
                                ButtonDefaults.outlinedButtonColors(contentColor = PhoenixBlue)
                        ) {
                            Text(if (entry.address == activeAddress) t.active() else t.select())
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = entry.polling,
                                onCheckedChange = { onTogglePolling(entry.address, it) }
                            )
                            Text(t.notifyBlocks())
                        }
                    }

                    OutlinedButton(
                        onClick = { onDelete(entry.address) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t.delete())
                    }
                }
            }
        }
    }
}

@Composable
fun Dashboard(s:UiState, t:Texts, explorerApi:String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(t.balance(), "%.8f".format(s.balance / SATS_PER_BTCX), "BTCX", Modifier.weight(1f))
            StatCard(t.blocks(), s.won.size.toString(), t.won(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(t.incoming(), s.incoming.size.toString(), t.tx(), Modifier.weight(1f))
            StatCard(t.outgoing(), s.outgoing.size.toString(), t.tx(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        StatsDashboard(s, t)

        Spacer(Modifier.height(16.dp))

        ExpandSectionPaged(t.wonBlocks(s.won.size), s.won, t = t) { MiningBlockCard(it, t, explorerApi) }
        ExpandSectionPaged(t.incomingTx(s.incoming.size), s.incoming, t = t) { TxCard(it, true, t, explorerApi) }
        ExpandSectionPaged(t.outgoingTx(s.outgoing.size), s.outgoing, t = t) { TxCard(it, false, t, explorerApi) }
    }
}

@Composable
fun StatsDashboard(s:UiState, t:Texts) {
    val totalReward = s.won.sumOf { it.reward }
    val avgReward = if (s.won.isNotEmpty()) totalReward / s.won.size else 0L
    val latest = s.won.maxByOrNull { it.height }
    val totalIncoming = s.incoming.sumOf { it.amount }
    val totalOutgoing = s.outgoing.sumOf { it.amount }
    val avgBlocksPerDay = calculateAverageBlocksPerDay(s.won)

    ExpandSectionSimple(t.stats()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(t.totalRewards(), "%.8f".format(totalReward / SATS_PER_BTCX), "BTCX", Modifier.weight(1f))
                StatCard(t.avgReward(), "%.8f".format(avgReward / SATS_PER_BTCX), "BTCX", Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(t.avgBlocksPerDay(), "%.3f".format(avgBlocksPerDay), "", Modifier.weight(1f))
                StatCard(t.txVolumeIn(), "%.8f".format(totalIncoming / SATS_PER_BTCX), "BTCX", Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(t.txVolumeOut(), "%.8f".format(totalOutgoing / SATS_PER_BTCX), "BTCX", Modifier.weight(1f))
                StatCard(t.latestBlock(), latest?.height?.toString() ?: "-", "", Modifier.weight(1f))
            }
        }
    }
}

fun calculateAverageBlocksPerDay(won:List<MiningBlock>):Double {
    val times = won.map { it.time }.filter { it > 0 }
    if (times.size <= 1) return times.size.toDouble()

    val minTime = times.minOrNull() ?: return 0.0
    val maxTime = times.maxOrNull() ?: return 0.0
    val days = max(1.0, (maxTime - minTime).toDouble() / 86400.0)

    return won.size.toDouble() / days
}

@Composable
fun StatCard(title:String, value:String, subtitle:String, modifier:Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = PhoenixBlue),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White)
            Text(value, color = PhoenixGreen, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = Color.White)
        }
    }
}

@Composable
fun ExpandSectionSimple(title:String, content:@Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }

    Button(
        onClick = { open = !open },
        colors = ButtonDefaults.buttonColors(containerColor = PhoenixDarkBlue),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(if (open) "▼ $title" else "▶ $title")
    }

    if (open) content()
}

@Composable
fun <T> ExpandSectionPaged(
    title:String,
    items:List<T>,
    pageSize:Int = 20,
    t:Texts,
    itemContent:@Composable (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }

    val maxPage = if (items.isEmpty()) 0 else (items.size - 1) / pageSize
    if (page > maxPage) page = maxPage

    val from = page * pageSize
    val to = minOf(from + pageSize, items.size)
    val visibleItems = if (items.isEmpty()) emptyList() else items.subList(from, to)

    Button(
        onClick = { open = !open },
        colors = ButtonDefaults.buttonColors(containerColor = PhoenixDarkBlue),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(if (open) "▼ $title" else "▶ $title")
    }

    if (open) {
        if (items.isEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = PhoenixCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(t.noEntries(), Modifier.padding(14.dp))
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { if (page > 0) page-- },
                    enabled = page > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixBlue)
                ) {
                    Text(t.previous())
                }

                Text(t.page(page + 1, maxPage + 1), modifier = Modifier.padding(top = 12.dp))

                Button(
                    onClick = { if (page < maxPage) page++ },
                    enabled = page < maxPage,
                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixBlue)
                ) {
                    Text(t.next())
                }
            }

            visibleItems.forEach { itemContent(it) }
        }
    }
}

@Composable
fun MiningBlockCard(b:MiningBlock, t:Texts, explorerApi:String) {
    val ctx = LocalContext.current

    Card(
        Modifier.fillMaxWidth().padding(vertical=5.dp),
        colors = CardDefaults.cardColors(containerColor = PhoenixCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("${t.blockHeight()}: ${b.height}", style = MaterialTheme.typography.titleMedium)
            Text("${t.reward()}: %.8f BTCX".format(b.reward / SATS_PER_BTCX))
            Text("${t.time()}: ${formatTime(b.time)}")
            TxIdText(ctx, b.txid, explorerApi)
        }
    }
}

@Composable
fun TxCard(tx:PocxTransaction, incoming:Boolean, t:Texts, explorerApi:String) {
    val ctx = LocalContext.current

    Card(
        Modifier.fillMaxWidth().padding(vertical=5.dp),
        colors = CardDefaults.cardColors(containerColor = PhoenixCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(if (incoming) t.input() else t.output(), style = MaterialTheme.typography.titleMedium)
            Text("${t.amount()}: %.8f BTCX".format(tx.amount / SATS_PER_BTCX))
            Text("${t.time()}: ${formatTime(tx.time)}")
            Text("${t.status()}: ${if (tx.confirmed) t.confirmed() else t.unconfirmed()}")
            tx.height?.let { Text("${t.blockHeight()}: $it") }
            TxIdText(ctx, tx.txid, explorerApi)
        }
    }
}

@Composable
fun TxIdText(ctx:Context, txid:String, explorerApi:String) {
    Text(
        "TX_ID: $txid",
        color = PhoenixBlue,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable {
                copyTxId(ctx, txid)
                openTxInExplorer(ctx, txid, explorerApi)
            }
    )
}

suspend fun loadState(address:String, t:Texts, explorerApi:String): UiState = withContext(Dispatchers.IO) {
    try {
        val api = createApi(explorerApi)
        val currentHeight = api.tipHeight()
        val info = api.address(address)
        val txs = fetchAllTxs(api, address).sortedByDescending { it.status.block_time ?: 0 }

        val won = detectMiningBlocks(address, txs)
        val incoming = detectIncoming(address, txs)
        val outgoing = detectOutgoing(address, txs)

        val balance =
            (info.chain_stats.funded_txo_sum - info.chain_stats.spent_txo_sum) +
                    (info.mempool_stats.funded_txo_sum - info.mempool_stats.spent_txo_sum)

        UiState(
            loading = false,
            balance = balance,
            txCount = info.chain_stats.tx_count + info.mempool_stats.tx_count,
            currentHeight = currentHeight,
            won = won,
            incoming = incoming,
            outgoing = outgoing
        )
    } catch(e: HttpException) {
        if (e.code() == 400 || e.code() == 404) {
            UiState(false, t.addressNotFound())
        } else {
            UiState(false, "HTTP ${e.code()}")
        }
    } catch(e: Exception) {
        UiState(false, e.message ?: e.toString())
    }
}

suspend fun fetchAllTxs(api:EsploraApi, address:String):List<Tx> {
    val out = mutableListOf<Tx>()
    var page = api.txs(address)
    out += page

    while (page.size >= 25) {
        val last = page.last().txid
        page = api.txsAfter(address, last)
        if (page.isEmpty()) break
        out += page
        if (out.size > 1000) break
    }

    return out.distinctBy { it.txid }
}

fun detectMiningBlocks(address:String, txs:List<Tx>):List<MiningBlock> =
    txs.mapNotNull { tx ->
        if (!tx.vin.any { it.is_coinbase } || tx.status.block_height == null) null
        else {
            val reward = tx.vout.filter { it.scriptpubkey_address == address }.sumOf { it.value }
            if (reward > 0) MiningBlock(tx.txid, tx.status.block_height, tx.status.block_time ?: 0, reward) else null
        }
    }

fun detectIncoming(address:String, txs:List<Tx>):List<PocxTransaction> =
    txs.mapNotNull { tx ->
        val received = tx.vout.filter { it.scriptpubkey_address == address }.sumOf { it.value }
        val spent = tx.vin.filter { it.prevout?.scriptpubkey_address == address }.sumOf { it.prevout?.value ?: 0 }
        val net = received - spent

        if (net > 0 && !tx.vin.any { it.is_coinbase }) {
            PocxTransaction(tx.txid, tx.status.block_time ?: 0, net, tx.status.block_height, tx.status.confirmed)
        } else null
    }

fun detectOutgoing(address:String, txs:List<Tx>):List<PocxTransaction> =
    txs.mapNotNull { tx ->
        val received = tx.vout.filter { it.scriptpubkey_address == address }.sumOf { it.value }
        val spent = tx.vin.filter { it.prevout?.scriptpubkey_address == address }.sumOf { it.prevout?.value ?: 0 }
        val net = received - spent

        if (net < 0) {
            PocxTransaction(tx.txid, tx.status.block_time ?: 0, kotlin.math.abs(net), tx.status.block_height, tx.status.confirmed)
        } else null
    }

suspend fun checkForNewBlocksForEnabledAddresses(
    ctx:Context,
    prefs:SharedPreferences,
    explorerApi:String,
    lang:Lang,
    notifyOnFirst:Boolean
) = withContext(Dispatchers.IO) {
    if (!loadGlobalPushEnabled(prefs)) return@withContext

    val entries = loadAddressEntries(prefs).filter { it.polling }
    val t = Texts(lang)

    entries.forEach { entry ->
        try {
            val oldTx = prefs.getString("lastWonTx_${entry.address}", null)
            val oldHeight = prefs.getLong("lastWonHeight_${entry.address}", -1L)

            val state = loadState(entry.address, t, explorerApi)
            val newest = state.won.maxByOrNull { it.height }

            if (newest != null) {
                val isNew = newest.txid != oldTx && newest.height > oldHeight

                if ((oldTx != null && isNew) || (oldTx == null && notifyOnFirst)) {
                    notify(ctx, entry, newest, lang, explorerApi)
                }

                prefs.edit()
                    .putString("lastWonTx_${entry.address}", newest.txid)
                    .putLong("lastWonHeight_${entry.address}", newest.height)
                    .apply()
            }
        } catch (_: Exception) {}
    }
}

class BlockCheckWorker(ctx:Context, params:WorkerParameters): CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("pocx", Context.MODE_PRIVATE)
        val explorerApi = loadExplorerApi(prefs)
        val lang = if (prefs.getString("lang", "DE") == "EN") Lang.EN else Lang.DE

        checkForNewBlocksForEnabledAddresses(
            ctx = applicationContext,
            prefs = prefs,
            explorerApi = explorerApi,
            lang = lang,
            notifyOnFirst = false
        )

        return Result.success()
    }
}

fun scheduleWorker(ctx:Context) {
    val req = PeriodicWorkRequestBuilder<BlockCheckWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
        "pocx-block-check",
        ExistingPeriodicWorkPolicy.UPDATE,
        req
    )
}

fun ensureNotificationChannel(ctx:Context) {
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(
                    "pocx",
                    "PoCX Blocks",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "PoCX / BTCX block win notifications"
                    enableVibration(true)
                    enableLights(true)
                }
            )
    }
}

fun sendTestNotification(ctx:Context, lang:Lang) {
    val title = if (lang == Lang.DE) "✅ PoCX Test-Benachrichtigung" else "✅ PoCX test notification"
    val text = if (lang == Lang.DE)
        "Benachrichtigungen funktionieren."
    else
        "Notifications are working."

    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
        999001,
        NotificationCompat.Builder(ctx,"pocx")
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
    )
}

fun notify(ctx:Context, entry:AddressEntry, b:MiningBlock, lang:Lang, explorerApi:String) {
    val title = if (lang == Lang.DE) "🔥 PoCX Block gewonnen!" else "🔥 PoCX block won!"
    val rewardText = "%.8f BTCX".format(b.reward / SATS_PER_BTCX)
    val shortAddr = shortAddress(entry.address)
    val displayName = if (entry.label.isNotBlank()) entry.label else shortAddr

    val body = if (lang == Lang.DE) {
        "Adresse: $displayName\n$shortAddr\nBlock: ${b.height}\nReward: $rewardText\nTX_ID: ${b.txid}"
    } else {
        "Address: $displayName\n$shortAddr\nBlock: ${b.height}\nReward: $rewardText\nTX_ID: ${b.txid}"
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(txUrl(explorerApi, b.txid)))
    val pendingIntent = PendingIntent.getActivity(
        ctx,
        b.txid.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
        b.txid.hashCode(),
        NotificationCompat.Builder(ctx,"pocx")
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title)
            .setContentText("$displayName · Block ${b.height} · $rewardText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(displayName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setGroup("pocx_block_wins")
            .build()
    )
}

fun openTxInExplorer(ctx:Context, txid:String, explorerApi:String) {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(txUrl(explorerApi, txid))))
}

fun copyTxId(ctx:Context, txid:String) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TX_ID", txid))
}

fun normalizeApiUrl(url:String):String {
    var u = url.trim()
    if (u.isBlank()) u = DEFAULT_EXPLORER_API
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
    if (!u.endsWith("/")) u += "/"
    return u
}

fun explorerWebBase(explorerApi:String):String {
    val api = normalizeApiUrl(explorerApi)
    return if (api.endsWith("/api/")) api.removeSuffix("/api/") + "/" else api
}

fun txUrl(explorerApi:String, txid:String):String =
    explorerWebBase(explorerApi) + "tx/$txid"

fun loadExplorerApi(prefs:SharedPreferences):String =
    normalizeApiUrl(prefs.getString("explorer_api", DEFAULT_EXPLORER_API) ?: DEFAULT_EXPLORER_API)

fun saveExplorerApi(prefs:SharedPreferences, url:String) {
    prefs.edit().putString("explorer_api", normalizeApiUrl(url)).apply()
}

fun loadGlobalPushEnabled(prefs:SharedPreferences):Boolean =
    prefs.getBoolean("global_push_enabled", true)

fun saveGlobalPushEnabled(prefs:SharedPreferences, enabled:Boolean) {
    prefs.edit().putBoolean("global_push_enabled", enabled).apply()
}

fun shortAddress(address:String):String =
    if (address.length <= 16) address else "${address.take(10)}…${address.takeLast(8)}"

fun formatTime(epoch:Long):String =
    if (epoch <= 0) "unbekannt"
    else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(epoch * 1000))

fun defaultAddressLabel(address:String):String =
    when (address) {
        "pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv" -> "Nogrod PoCX Mining Pool"
        "pocx1qadr88lh9nre4asm2qvlzhjtypgj7rx2v47aufz" -> "Meine PoCX Adresse"
        else -> shortAddress(address)
    }

fun encodePart(value:String):String =
    value.replace("|", " ").replace("\n", " ").trim()

fun loadAddressEntries(prefs:SharedPreferences):List<AddressEntry> {
    val raw = prefs.getString("addresses_v2", null)

    if (raw.isNullOrBlank()) {
        val legacy = prefs.getString("address", DEFAULT_ADDRESS) ?: DEFAULT_ADDRESS
        return listOf(AddressEntry(legacy, true, defaultAddressLabel(legacy))).distinctBy { it.address }
    }

    return raw.lines().mapNotNull { line ->
        val parts = line.split("|", limit = 3)
        val addr = parts.getOrNull(0)?.trim().orEmpty()

        if (addr.isEmpty()) null
        else {
            val polling = parts.getOrNull(1) != "0"
            val label = parts.getOrNull(2)?.trim().orEmpty().ifBlank { defaultAddressLabel(addr) }
            AddressEntry(addr, polling, label)
        }
    }.distinctBy { it.address }.ifEmpty {
        listOf(AddressEntry(DEFAULT_ADDRESS, true, defaultAddressLabel(DEFAULT_ADDRESS)))
    }
}

fun saveAddressEntries(prefs:SharedPreferences, entries:List<AddressEntry>) {
    val raw = entries
        .distinctBy { it.address }
        .joinToString("\n") {
            "${encodePart(it.address)}|${if (it.polling) "1" else "0"}|${encodePart(it.label)}"
        }

    prefs.edit().putString("addresses_v2", raw).apply()
}