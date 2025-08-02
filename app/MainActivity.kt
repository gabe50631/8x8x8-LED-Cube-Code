class MainActivity : ComponentActivity() {

  private lateinit var bluetoothManager: BluetoothManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    bluetoothManager = BluetoothManager(this)

    checkPermissionsAndStartScan()

    setContent {
      MaterialTheme {
        AnimationControlScreen { byte -> bluetoothManager.sendCommand(byte) }
      }
    }
  }

  private fun checkPermissionsAndStartScan() {
    val requiredPermissions = arrayOf(
      Manifest.permission.BLUETOOTH,
      Manifest.permission.BLUETOOTH_ADMIN,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.ACCESS_FINE_LOCATION
    )

    if (requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
      }) {
      bluetoothManager.scanForDevices()
    } else {
      ActivityCompat.requestPermissions(this, requiredPermissions, 1)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
  ) {
    if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
      bluetoothManager.scanForDevices()
    } else {
      Toast.makeText(this, "Permissions required for BLE", Toast.LENGTH_SHORT).show()
    }
  }
}
