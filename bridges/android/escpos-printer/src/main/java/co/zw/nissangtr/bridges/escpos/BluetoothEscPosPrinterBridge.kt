package co.zw.nissangtr.bridges.escpos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Android [EscPosPrinterBridge] via Bluetooth RFCOMM (SPP) or Wi-Fi/LAN raw TCP.
 *
 * Set printer MAC with [setPrinterAddress] (persisted) before [connect].
 * Host must [attachActivity] before [requestBluetoothPermission].
 *
 * Sends ESC/POS bytes directly to the selected printer transport — never through Supabase.
 */
class BluetoothEscPosPrinterBridge(
    context: Context,
) : EscPosPrinterBridge {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var activityRef: WeakReference<Activity>? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var networkSocket: Socket? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity() {
        activityRef = null
    }

    override fun selectTransport(transport: PrinterTransport) {
        prefs.edit().putString(KEY_TRANSPORT, transport.name).apply()
    }

    override fun getConfiguredTransport(): PrinterTransport =
        runCatching {
            PrinterTransport.valueOf(prefs.getString(KEY_TRANSPORT, PrinterTransport.BLUETOOTH.name)!!)
        }.getOrDefault(PrinterTransport.BLUETOOTH)

    override fun configureNetworkPrinter(host: String, port: Int) {
        val cleanHost = host.trim()
        require(cleanHost.isNotEmpty()) { "Printer host required" }
        require(port in 1..65535) { "Printer port must be 1..65535" }
        prefs.edit()
            .putString(KEY_HOST, cleanHost)
            .putInt(KEY_PORT, port)
            .putString(KEY_TRANSPORT, PrinterTransport.WIFI.name)
            .apply()
    }

    override fun getConfiguredNetworkHost(): String? =
        prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() }

    override fun getConfiguredNetworkPort(): Int = prefs.getInt(KEY_PORT, DEFAULT_NETWORK_PORT)

    /** Persist / update the bonded printer Bluetooth MAC (e.g. `00:11:22:33:44:55`). */
    override fun configurePrinterAddress(macAddress: String) {
        prefs.edit().putString(KEY_MAC, macAddress.trim()).putString(KEY_TRANSPORT, PrinterTransport.BLUETOOTH.name).apply()
    }

    /** @deprecated Prefer [configurePrinterAddress] (contract-aligned). */
    fun setPrinterAddress(macAddress: String) = configurePrinterAddress(macAddress)

    fun getPrinterAddress(): String? =
        prefs.getString(KEY_MAC, null)?.takeIf { it.isNotBlank() }

    override fun getConfiguredPrinterAddress(): String? = getPrinterAddress()

    @SuppressLint("MissingPermission")
    override suspend fun listBondedDevices(): List<BondedEscPosDevice> =
        withContext(Dispatchers.IO) {
            ensureBluetoothAllowed()
            val adapter = bluetoothAdapter()
                ?: throw IllegalStateException("Bluetooth adapter unavailable")
            adapter.bondedDevices.orEmpty()
                .map { device ->
                    BondedEscPosDevice(
                        name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown",
                        address = device.address,
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

    fun onPermissionResult() {
        BluetoothPermissionRelay.complete(resolvePermissionStatus())
    }

    override suspend fun getBluetoothPermissionStatus(): BluetoothPermissionStatus =
        withContext(Dispatchers.Main) { resolvePermissionStatus() }

    override suspend fun requestBluetoothPermission(): BluetoothPermissionStatus =
        withContext(Dispatchers.Main) {
            val status = resolvePermissionStatus()
            if (status == BluetoothPermissionStatus.GRANTED) return@withContext status
            val activity = activityRef?.get()
                ?: return@withContext BluetoothPermissionStatus.NOT_DETERMINED
            val needed = requiredPermissions().filter {
                ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isEmpty()) return@withContext BluetoothPermissionStatus.GRANTED
            suspendCancellableCoroutine { cont ->
                BluetoothPermissionRelay.arm(cont)
                cont.invokeOnCancellation { BluetoothPermissionRelay.cancel() }
                ActivityCompat.requestPermissions(
                    activity,
                    needed.toTypedArray(),
                    REQUEST_BLUETOOTH,
                )
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        when (getConfiguredTransport()) {
            PrinterTransport.BLUETOOTH -> {
                ensureBluetoothAllowed()
                val mac = getPrinterAddress()
                    ?: throw IllegalStateException("Printer Bluetooth MAC not set")
                val adapter = bluetoothAdapter()
                    ?: throw IllegalStateException("Bluetooth adapter unavailable")
                if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is disabled")
                val device = adapter.getRemoteDevice(mac)
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                try {
                    sock.connect()
                } catch (e: IOException) {
                    runCatching { sock.close() }
                    throw IOException("ESC/POS Bluetooth connect failed for $mac", e)
                }
                socket = sock
            }
            PrinterTransport.WIFI -> {
                val host = getConfiguredNetworkHost()
                    ?: throw IllegalStateException("Printer Wi-Fi host/IP not set")
                val port = getConfiguredNetworkPort()
                val sock = Socket()
                try {
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(host, port), NETWORK_CONNECT_TIMEOUT_MS)
                } catch (e: IOException) {
                    runCatching { sock.close() }
                    throw IOException("ESC/POS Wi-Fi connect failed for $host:$port", e)
                }
                networkSocket = sock
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
    }

    override suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        when (getConfiguredTransport()) {
            PrinterTransport.BLUETOOTH -> socket?.isConnected == true
            PrinterTransport.WIFI -> networkSocket?.let { it.isConnected && !it.isClosed } == true
        }
    }

    override suspend fun printInventoryLabel(job: EscPosPrintJob) {
        require(job.qrPayload.startsWith("gtr://part/")) {
            "qrPayload must be inventory gtr://part/… URI (no fiscal/ZIMRA payloads)"
        }
        printRaw(EscPosCommands.inventoryLabel(job))
    }

    override suspend fun printReceiptLines(lines: List<EscPosReceiptLine>) {
        require(lines.isNotEmpty()) { "receipt lines required" }
        printRaw(EscPosCommands.receiptLines(lines))
    }

    override suspend fun printRaw(bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            when (getConfiguredTransport()) {
                PrinterTransport.BLUETOOTH -> {
                    ensureBluetoothAllowed()
                    val sock = socket
                    if (sock == null || !sock.isConnected) {
                        throw IllegalStateException("Bluetooth printer not connected")
                    }
                    sock.outputStream.write(bytes)
                    sock.outputStream.flush()
                }
                PrinterTransport.WIFI -> {
                    val sock = networkSocket
                    if (sock == null || !sock.isConnected || sock.isClosed) {
                        throw IllegalStateException("Wi-Fi printer not connected")
                    }
                    sock.getOutputStream().write(bytes)
                    sock.getOutputStream().flush()
                }
            }
        } catch (e: IOException) {
            disconnectInternal()
            throw IOException("ESC/POS write failed", e)
        }
    }

    private fun disconnectInternal() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        try {
            networkSocket?.close()
        } catch (_: IOException) {
        }
        networkSocket = null
    }

    private fun ensureBluetoothAllowed() {
        val status = resolvePermissionStatus()
        if (status != BluetoothPermissionStatus.GRANTED) {
            throw SecurityException("Bluetooth permission not granted ($status)")
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }

    private fun resolvePermissionStatus(): BluetoothPermissionStatus {
        val missing = requiredPermissions().any {
            ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
        }
        if (!missing) return BluetoothPermissionStatus.GRANTED
        val activity = activityRef?.get()
        val showRationale = activity != null && requiredPermissions().any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        return if (showRationale) {
            BluetoothPermissionStatus.DENIED
        } else {
            BluetoothPermissionStatus.NOT_DETERMINED
        }
    }

    companion object {
        const val REQUEST_BLUETOOTH: Int = 0x45_50 // "EP"
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREFS = "gtr_escpos_printer"
        private const val KEY_MAC = "printer_mac"
        private const val KEY_TRANSPORT = "printer_transport"
        private const val KEY_HOST = "printer_host"
        private const val KEY_PORT = "printer_port"
        private const val DEFAULT_NETWORK_PORT = 9100
        private const val NETWORK_CONNECT_TIMEOUT_MS = 5_000
    }
}
