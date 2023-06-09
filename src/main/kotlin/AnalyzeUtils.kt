import java.io.InputStream
import java.lang.Exception

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

data class UData(
    val unixTimeStamp: UInt,
    val algo: UByte,
    val validated: Boolean,
    val sub: Boolean,
    val domainName: List<String>
)

fun readUData(inputStream: InputStream, index: Int): Pair<Int, UData> {
    val dataLength = 9
    val data = inputStream.readNBytes(dataLength)
    val unixTimeStamp = data.getUIntAt(0)
    val algo = data[5].toUByte()
    val validated = verificationSucceeded(data.get(6))
    val sub = data[7] == 0.toByte()
    val length = data[8].toUByte()
    val domainNameDNS = inputStream.readNBytes(length.toInt())
    assertOrExit(inputStream.read() == '\n'.code, "No newline at $index")

    val domainName = domainNameToString(domainNameDNS)
    val readAmount = dataLength + length.toInt() + 1
    return Pair(readAmount, UData(unixTimeStamp, algo, validated, sub, domainName))
}

suspend fun readAllUData(fileLocation: String): List<UData> {
    val uDataList = mutableListOf<UData>()
    readFileWithProgress(fileLocation) { inputStream, index ->
        val (readAmount, ud) = readUData(inputStream, index)
        uDataList.add(ud)
        readAmount
    }
    return uDataList
}