package com.example.ledcubeapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.ledcubeapp.ui.theme.LEDCubeAppTheme
import java.util.*

class MainActivity : ComponentActivity() {
  private val TAG = "LEDCubeBLE"

  // UUIDs (MUST match ESP32)
  private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
  private val COMMAND_CHARACTERISTIC_UUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")
  private val TEXT_CHARACTERISTIC_UUID = UUID.fromString("abcd5678-ab12-cd34-ef56-abcdef123456")

  private var bleGatt: BluetoothGatt? = null
  private var commandCharacteristic: BluetoothGattCharacteristic? = null
  private var textCharacteristic: BluetoothGattCharacteristic? = null

  private var scanner: BluetoothLeScanner? = null
  private var scanCallback: ScanCallback? = null
  private val SCAN_TIMEOUT_MS = 10_000L
  private val deviceName = "ESP32-BLE-Controller"

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { map ->
    Log.d(TAG, "Permissions result: $map")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // request permissions based on SDK level
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.BLUETOOTH_CONNECT
        )
      )
    } else {
      permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    setContent {
      LEDCubeAppTheme {
        Scaffold { inner ->
          BLEControlScreen(modifier = Modifier.padding(inner))
        }
      }
    }
  }

  @Composable
  fun BLEControlScreen(modifier: Modifier = Modifier) {
    var isConnected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Idle") }
    var textInput by remember { mutableStateOf("") }

    Column(modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text("ESP32 BLE Control")
      Text("Status: $statusText")

      Button(onClick = {
        statusText = "Scanning..."
        startScanAndConnect { success ->
          isConnected = success
          statusText = if (success) "Connected" else "Not connected"
        }
      }, modifier = Modifier.fillMaxWidth()) {
        Text("Scan & Connect")
      }

      Button(onClick = { if (isConnected) { sendCommand(1); statusText = "Running testPlanes" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Test Planes")
      }

      Button(onClick = { if (isConnected) { sendCommand(2); statusText = "Running rain" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Rain")
      }

      Button(onClick = { if (isConnected) { sendCommand(3); statusText = "Running randomGenerate" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Random Generate")
      }

      Button(onClick = { if (isConnected) { sendCommand(4); statusText = "Running concentricCubes" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Concentric Cubes")
      }

      Button(onClick = { if (isConnected) { sendCommand(5); statusText = "Running wave" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Wave (z)")
      }

      Button(onClick = { if (isConnected) { sendCommand(6); statusText = "Running wave" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Wave (y)")
      }

      Button(onClick = { if (isConnected) { sendCommand(7); statusText = "Running wave" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Wave (x)")
      }

      Button(onClick = { if (isConnected) { sendCommand(8); statusText = "Running wave2" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Wave 2.0")
      }

      Button(onClick = { if (isConnected) { sendCommand(9); statusText = "Running fireworks" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Fireworks")
      }

      Button(onClick = { if (isConnected) { sendCommand(10); statusText = "Running clearCube" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("Clear")
      }Button(onClick = { if (isConnected) { sendCommand(1); statusText = "Running testPlanes" } },
      modifier = Modifier.fillMaxWidth()) {
      Text("testPlanes")
    }

      Button(onClick = { if (isConnected) { sendCommand(2); statusText = "Running rain" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("rain")
      }

      Button(onClick = { if (isConnected) { sendCommand(3); statusText = "Running randomGenerate" } },
        modifier = Modifier.fillMaxWidth()) {
        Text("randomGenerate")
      }

      OutlinedTextField(value = textInput, onValueChange = { textInput = it },
        label = { Text("Text (Function 11)") }, modifier = Modifier.fillMaxWidth())

      Button(onClick = {
        if (isConnected) {
          sendText(textInput)
          statusText = "Text sent"
        } else {
          statusText = "Not connected"
        }
      }, modifier = Modifier.fillMaxWidth()) {
        Text("Send Text")
      }
    }
  }

  // start scanning with a filter for the service UUID, stop after timeout
  private fun startScanAndConnect(callback: (Boolean) -> Unit) {
    val btAdapter = BluetoothAdapter.getDefaultAdapter()
    if (btAdapter == null || !btAdapter.isEnabled) {
      Log.e(TAG, "Bluetooth not available or disabled")
      callback(false)
      return
    }

    // permission checks
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "BLUETOOTH_SCAN not granted")
        callback(false)
        return
      }
    } else {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.e(TAG, "ACCESS_FINE_LOCATION not granted")
        callback(false)
        return
      }
    }

    scanner = btAdapter.bluetoothLeScanner
    if (scanner == null) {
      Log.e(TAG, "bluetoothLeScanner is null")
      callback(false)
      return
    }

    // filter by service UUID (fast and reliable)
    val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    scanCallback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        val dev = result.device
        Log.d(TAG, "Scan hit: name=${dev.name} addr=${dev.address}")

        // match by name or by service UUID filter (filter already used)
        if (dev.name != null && dev.name == deviceName) {
          stopScan()
          Log.d(TAG, "Found device by name -> connecting")
          startGattConnect(dev, callback)
        } else {
          // if you want to match by advertisement service data/details, add logic here
        }
      }

      override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.e(TAG, "Scan failed: $errorCode")
        stopScan()
        callback(false)
      }
    }

    try {
      scanner?.startScan(listOf(filter), settings, scanCallback)
      Handler(Looper.getMainLooper()).postDelayed({
        if (scanCallback != null) {
          Log.d(TAG, "Scan timed out")
          stopScan()
          callback(false)
        }
      }, SCAN_TIMEOUT_MS)
    } catch (e: Exception) {
      Log.e(TAG, "startScan threw: ${e.message}")
      callback(false)
    }
  }

  private fun stopScan() {
    try {
      if (scanner != null && scanCallback != null) {
        scanner?.stopScan(scanCallback)
      }
    } catch (e: Exception) {
      Log.w(TAG, "stopScan error: ${e.message}")
    } finally {
      scanCallback = null
    }
  }

  @SuppressLint("MissingPermission")
  private fun startGattConnect(device: BluetoothDevice, callback: (Boolean) -> Unit) {
    // permission check for connect on Android 12+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT not granted")
      callback(false)
      return
    }

    bleGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (newState == BluetoothProfile.STATE_CONNECTED) {
          Log.d(TAG, "GATT connected, requesting MTU and discovering services")
          try {
            gatt.requestMtu(247)
          } catch (e: Exception) {
            Log.w(TAG, "requestMtu failed: ${e.message}")
          }
          gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          Log.d(TAG, "GATT disconnected")
          try { gatt.close() } catch (e: Exception) {}
          runOnUiThread { callback(false) }
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)
        Log.d(TAG, "Services discovered, status=$status")
        val service = gatt.getService(SERVICE_UUID)
        if (service != null) {
          Log.d(TAG, "Service found")
          commandCharacteristic = service.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
          textCharacteristic = service.getCharacteristic(TEXT_CHARACTERISTIC_UUID)
          Log.d(TAG, "CommandChar found=${commandCharacteristic != null} TextChar found=${textCharacteristic != null}")
          runOnUiThread { callback(true) }
        } else {
          Log.e(TAG, "Service not found")
          runOnUiThread { callback(false) }
        }
      }

      override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        val valBytes = characteristic.value
        val str = valBytes?.toString(Charsets.UTF_8) ?: "<empty>"
        Log.d(TAG, "onCharacteristicWrite status=$status uuid=${characteristic.uuid} value=$str")
      }

      override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        Log.d(TAG, "MTU changed -> $mtu status=$status")
      }
    })
  }

  // write a single-byte command 0x01..0x0A
  @SuppressLint("MissingPermission")
  private fun sendCommand(value: Byte) {
    val char = commandCharacteristic ?: run {
      Log.e(TAG, "Command characteristic missing")
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT not granted for write")
      return
    }

    char.value = byteArrayOf(value)
    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    val res = bleGatt?.writeCharacteristic(char)
    Log.d(TAG, "writeCharacteristic requested -> $res")
  }

  // write text (UTF-8). If API >= 33, try the three-arg overload; fallback to legacy.
  @SuppressLint("MissingPermission")
  private fun sendText(text: String) {
    val char = textCharacteristic ?: run {
      Log.e(TAG, "Text characteristic missing")
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      Log.e(TAG, "BLUETOOTH_CONNECT not granted for write")
      return
    }

    val bytes = text.toByteArray(Charsets.UTF_8)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // API 33+ has writeCharacteristic with bytes param
      val ok = bleGatt?.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
      Log.d(TAG, "writeCharacteristic (API33) -> $ok")
    } else {
      char.value = bytes
      char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      val ok = bleGatt?.writeCharacteristic(char)
      Log.d(TAG, "writeCharacteristic (legacy) -> $ok")
    }
  }

  override fun onDestroy() {
    try {
      bleGatt?.close()
      bleGatt = null
    } catch (e: Exception) {}
    super.onDestroy()
  }
}
