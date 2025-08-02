class BluetoothManager(private val context: Context) {

  private var bluetoothGatt: BluetoothGatt? = null
  private var writeCharacteristic: BluetoothGattCharacteristic? = null

  private val serviceUUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
  private val characteristicUUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")

  private val scanner: BluetoothLeScanner by lazy {
    BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
  }

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val device = result.device
      if (device.name == "ESP32-BLE-Controller") {
        scanner.stopScan(this)
        connectToDevice(device)
      }
    }
  }

  fun scanForDevices() {
    scanner.startScan(scanCallback)
  }

  private fun connectToDevice(device: BluetoothDevice) {
    device.connectGatt(context, false, object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
          bluetoothGatt = gatt
          gatt.discoverServices()
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val service = gatt.getService(serviceUUID)
        writeCharacteristic = service?.getCharacteristic(characteristicUUID)
      }
    })
  }

  fun sendCommand(value: Byte) {
    writeCharacteristic?.let {
      it.value = byteArrayOf(value)
      bluetoothGatt?.writeCharacteristic(it)
    } ?: Log.w("BLE", "Characteristic not ready")
  }
}
