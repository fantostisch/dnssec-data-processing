fun verificationSucceeded(byte: Byte): Boolean {
    return byte == 1.toByte()
}

fun domainNameToString(byteArray: ByteArray): List<String> {
    if (byteArray.isEmpty()) {
        throw IllegalArgumentException()
    }
    if (byteArray.size == 1) {
        return listOf("")
    }
    val length = byteArray[0].toUByte().toInt()
    return listOf(
        String(
            byteArray,
            1,
            length
        )
    ) + domainNameToString(byteArray.copyOfRange(length + 1, byteArray.size))
}

// Based on https://stackoverflow.com/a/53930838
fun ByteArray.getUIntAt(idx: Int) =
    ((this[idx + 3].toUInt() and 0xFFu) shl 24) or
            ((this[idx + 2].toUInt() and 0xFFu) shl 16) or
            ((this[idx + 1].toUInt() and 0xFFu) shl 8) or
            (this[idx].toUInt() and 0xFFu)
