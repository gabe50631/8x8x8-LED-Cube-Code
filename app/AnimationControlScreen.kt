@Composable
fun AnimationControlScreen(sendCommand: (Byte) -> Unit) {
  Column(modifier = Modifier
    .fillMaxSize()
    .padding(16.dp)) {

    Text("LED Cube Animation Controller", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(16.dp))

    val animations = listOf(
      "Planes" to 0x01,
      "Rain" to 0x02,
      "Random" to 0x03,
      "Concentric Cubes" to 0x04,
      "Wave Z" to 0x05,
      "Wave Y" to 0x06,
      "Wave X" to 0x07,
      "Wave 2" to 0x08,
      "Fireworks" to 0x09,
      "Clear Cube" to 0x0A
    )

    animations.forEach { (label, byteValue) ->
      Button(
        onClick = { sendCommand(byteValue.toByte()) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      ) {
        Text(label)
      }
    }
  }
}

