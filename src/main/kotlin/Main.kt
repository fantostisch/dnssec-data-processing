import java.io.File
import kotlin.system.exitProcess

fun verificationSucceeded(byte: Byte): Boolean {
    return byte == 5.toByte()
}

fun exit(message: String) {
    System.err.println(message)
    exitProcess(1)
}

fun domainNameToString(byteArray: ByteArray): List<String> {
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

fun getPublicSuffixes(): List<String> {
    val suffixFile = File("public_suffix_list.dat")
    return suffixFile.readLines().filter { l -> !l.startsWith("//") && l.isNotEmpty() }
}

data class Stats(var validations: Int, var successfullValidations: Int)


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exit("Please specify log file as argument.")
    }

    val suffixes = getPublicSuffixes()

    val stats = mutableMapOf<String, Stats>()

    val logFile = File(args[0])
    val inputStream = logFile.inputStream()
    var firstTimeStamp = 0u
    var lastTimestamp = 0u
    var index = 0
    var validationSuccess = 0
    while (inputStream.available() > 0) {
        val data = inputStream.readNBytes(8)
        val unixTimeStamp = data.getUIntAt(0)
        val algo = data.get(5).toUByte()
        val validated = verificationSucceeded(data.get(6))
        val length = data.get(7).toUByte()

        val domainNameDNS = inputStream.readNBytes(length.toInt())
        assert(inputStream.read() == '\n'.code)

        val domainName = domainNameToString(domainNameDNS)
        val domainNameString = domainName.dropLast(1).joinToString(".")

        if (domainName.size <= 2 || suffixes.contains(domainNameString)) {
            val stat = stats.get(domainNameString)
            if (stat != null) {
                stat.validations++
                if (validated) {
                    stat.successfullValidations++
                }
            } else {
                val validatedAmount = if (validated) {
                    1
                } else {
                    0
                }
                stats.put(domainNameString, Stats(1, validatedAmount))
            }
        }

        if (index == 0) {
            firstTimeStamp = unixTimeStamp
        }
        lastTimestamp = unixTimeStamp

        if (validated) {
            validationSuccess++
        }
        index++
    }
    inputStream.close()
    val out = File("out.csv")
    out.writeText("")
    out.appendText("Total amount of verifications: $index\n")
    out.appendText("Successfully validated: $validationSuccess\n")
    out.appendText("First validation: $firstTimeStamp\n")
    out.appendText("Last validation: $lastTimestamp\n")
    stats.toList().sortedBy { (domain, _) -> domain }.forEach { (domain, stat) ->
        out.appendText("$domain, ${stat.validations}, ${stat.successfullValidations}\n")
    }
    println("Done")
}
