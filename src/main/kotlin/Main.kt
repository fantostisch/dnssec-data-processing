import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import java.io.InputStream
import java.lang.Exception
import java.net.IDN

fun verificationSucceeded(byte: Byte): Boolean {
    return byte == 1.toByte()
}

fun exit(message: String) {
    System.err.println(message)
    exitProcess(1)
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

fun domainNameToList(byteArray: ByteArray): List<ByteArray> {
    if (byteArray.isEmpty()) {
        throw IllegalArgumentException()
    }
    if (byteArray.size == 1) {
        return listOf(ByteArray(0))
    }
    val length = byteArray[0].toUByte().toInt()
    if (length >= byteArray.size) {
        throw IllegalArgumentException()
    }
    val subdomain = byteArray.copyOfRange(1, length + 1)
    return listOf(subdomain) +
            domainNameToList(byteArray.copyOfRange(length + 1, byteArray.size))
}

fun listToDomainName(list: List<ByteArray>): ByteArray {
    val first = list.firstOrNull() ?: return ByteArray(0)
    return byteArrayOf(first.size.toUByte().toByte()) + first + listToDomainName(
        list.subList(
            1,
            list.size
        )
    )
}

// Based on https://stackoverflow.com/a/53930838
fun ByteArray.getUIntAt(idx: Int) =
    ((this[idx + 3].toUInt() and 0xFFu) shl 24) or
            ((this[idx + 2].toUInt() and 0xFFu) shl 16) or
            ((this[idx + 1].toUInt() and 0xFFu) shl 8) or
            (this[idx].toUInt() and 0xFFu)

fun getPublicSuffixes(): List<String> {
    val suffixFile = File("public_suffix_list.dat")
    return suffixFile.readLines(Charsets.UTF_8)
        .filter { l -> !l.startsWith("//") && l.isNotEmpty() }
        .map { s -> IDN.toASCII(s) }
}

data class Suffix(
    val suffix: List<ByteArray>,
    val wildcard: Boolean,
    val exceptions: List<List<ByteArray>>
)

fun getPublicSuffixBytes(): List<Suffix> {
    val allSuffixes = getPublicSuffixes()
        .map { suffix -> suffix.split('.').map { s -> s.encodeToByteArray() } }
    val normalSuffixes = mutableListOf<List<ByteArray>>()
    val wildcardSuffixes = mutableListOf<List<ByteArray>>()
    val exceptionSuffixes = mutableListOf<List<ByteArray>>()
    allSuffixes.forEach { s ->
        val wildcard = s.first().size == 1 && s.first()[0] == '*'.code.toByte()
        val exception = s.first()[0] == '!'.code.toByte()
        if (wildcard) {
            wildcardSuffixes.add(s.drop(1))
        } else if (exception) {
            val first = s.first()
            val noExclamationMark =
                listOf(first.copyOfRange(1, first.size)) + s.subList(1, s.size)
            exceptionSuffixes.add(noExclamationMark)
        } else {
            normalSuffixes.add(s)
        }
    }
    val wildcards = wildcardSuffixes.map { w ->
        val exceptions =
            exceptionSuffixes.filter { ex -> byteArrayListEquals(w, ex.drop(1)) }
        Suffix(w, true, exceptions)
    }
    val normal = normalSuffixes.map { s -> Suffix(s, false, listOf()) }
    return normal + wildcards
}


data class Stats(
    var validations: Int,
    var successfullValidations: Int,
    var times: List<UInt>,
    var peak: Double
)

val windowSeconds = 30u

fun updateTimes(times: List<UInt>, unixTimeStamp: UInt): List<UInt> {
    val newTimes = times.dropWhile { time -> time < unixTimeStamp - windowSeconds }
    return newTimes + unixTimeStamp
}

fun printHelp() {
    exit(
        "Usage: anonymize FILE_LOCATION\n" +
                "or: analyze FILE_LOCATION (only works on anonymized files)"
    )
}

fun assertOrExit(check: Boolean, message: String) {
    if (!check) {
        exit("Assertion failed: $message")
    }
}

val anonSuffix = "anon"

suspend fun main(args: Array<String>) {
    if (args.size < 2) {
        printHelp()
    }
    val fileLocation = args[1]
    when (args[0]) {
        "analyze" -> analyze(fileLocation)
        "anonymize" -> anonymize(fileLocation)
        "aa" -> {
            anonymize(fileLocation)
            analyze("$fileLocation.$anonSuffix")
        }

        else -> printHelp()
    }
    println("Done")
}

suspend fun readFileWithProgress(
    fileLocation: String,
    function: (InputStream, Int) -> Int
): Int {
    val logFile = File(fileLocation)
    val logFileSize = logFile.length()
    val inputStream = logFile.inputStream()
    var readBytes = 0
    var index = 0

    coroutineScope {
        val progressJob = launch {
            while (true) {
                delay(1000)
                val progress = readBytes.toDouble() / logFileSize.toDouble() * 100
                val progressString = "%.1f".format(progress)
                println("$progressString%")
            }
        }
        launch {
            while (inputStream.available() > 0) {
                readBytes += function(inputStream, index)
                index++
            }
            progressJob.cancel()
            inputStream.close()
        }
    }
    return index
}

fun byteArrayListEquals(a: List<ByteArray>, b: List<ByteArray>): Boolean {
    if (a.size != b.size) {
        return false
    }
    a.zip(b).forEach { (ab, bb) ->
        if (!ab.contentEquals(bb)) {
            return false
        }
    }
    return true
}

fun getSuffix(
    domain: List<ByteArray>,
    suffixes: List<Suffix>
): Pair<Boolean, List<ByteArray>>? {
    return getSuffixWithoutRoot(
        domain.dropLast(1),
        suffixes
    )?.let { (sub, domain) -> Pair(sub, domain + ByteArray(0)) }
}

fun isPartOf(domain: List<ByteArray>, suffix: List<ByteArray>): Boolean {
    val from = domain.size - suffix.size
    return if (from < 0) {
        false
    } else {
        byteArrayListEquals(domain.subList(from, domain.size), suffix)
    }
}

fun getSuffixWithoutRoot(
    domain: List<ByteArray>,
    suffixes: List<Suffix>
): Pair<Boolean, List<ByteArray>>? {
    val suffix = suffixes.filter { s ->
        isPartOf(domain, s.suffix)
    }.maxByOrNull { s -> s.suffix.size } ?: return null
    val domainSuffix = if (!suffix.wildcard) {
        suffix.suffix
    } else {
        val isException = suffix.exceptions.any { ex -> isPartOf(domain, ex) }
        if (isException) {
            suffix.suffix
        } else {
            domain.takeLast(suffix.suffix.size + 1)
        }
    }
    val sub = domain.size > domainSuffix.size
    return Pair(sub, domainSuffix)
}

fun anonymizeDomain(
    domainNameDNS: ByteArray,
    suffixes: List<Suffix>
): Pair<Boolean, List<ByteArray>>? {
    val domainName = try {
        domainNameToList(domainNameDNS)
    } catch (ex: Exception) {
        return null
    }
    return if (domainName.size == 1) {
        Pair(false, domainName)
    } else {
        getSuffix(domainName, suffixes)
    }
}

suspend fun anonymize(fileLocation: String) {
    val suffixes = getPublicSuffixBytes()

    val outFileSuccess = File("$fileLocation.$anonSuffix")
    outFileSuccess.writeText("")
    val outputStreamSuccess = outFileSuccess.outputStream()

    val outFileFailed = File("$fileLocation.failed")
    outFileFailed.writeText("")
    val outputStreamFailed = outFileFailed.outputStream()

    readFileWithProgress(fileLocation) { inputStream, index ->
        val dataLength = 8
        val data = inputStream.readNBytes(dataLength)
        val length = data[7].toUByte()
        val domainNameDNS = inputStream.readNBytes(length.toInt())

        fun writeFailed() {
            outputStreamFailed.write(data)
            outputStreamFailed.write(domainNameDNS)
            outputStreamFailed.write('\n'.code)
        }

        fun writeSuccess(sub: Boolean, domainSuffix: List<ByteArray>) {
            val subByte = if (sub) {
                1
            } else {
                0
            }
            outputStreamSuccess.write(data.copyOf(7))
            outputStreamSuccess.write(subByte)
            val anonymizedDomainBytes = listToDomainName(domainSuffix)
            outputStreamSuccess.write(anonymizedDomainBytes.size)
            outputStreamSuccess.write(anonymizedDomainBytes)
            outputStreamSuccess.write('\n'.code)
        }

        val domainSuffix = anonymizeDomain(domainNameDNS, suffixes)
        if (domainSuffix == null) {
            writeFailed()
        } else {
            writeSuccess(domainSuffix.first, domainSuffix.second)
        }

        assertOrExit(inputStream.read() == '\n'.code, "No newline at $index")
        return@readFileWithProgress dataLength + length.toInt() + 1
    }
    outputStreamSuccess.close()
    outputStreamFailed.close()
}

suspend fun analyze(fileLocation: String) {
    val stats = mutableMapOf<String, Stats>()

    var firstTimeStamp = 0u
    var lastTimestamp = 0u
    var validationSuccess = 0
    var errors = 0
    var allTimes = listOf<UInt>()
    var allPeak = 0.toDouble()
    var previousTime = 0u
    var notInOrder = 0
    val totalLoops = readFileWithProgress(fileLocation) { inputStream, index ->
        val dataLength = 9
        val data = inputStream.readNBytes(dataLength)
        val unixTimeStamp = data.getUIntAt(0)
        val algo = data.get(5).toUByte()
        val validated = verificationSucceeded(data.get(6))
        val sub = data.get(7).toUByte()
        val length = data.get(8).toUByte()

        val domainNameDNS = inputStream.readNBytes(length.toInt())
        assertOrExit(inputStream.read() == '\n'.code, "No newline at $index")

        val domainName = try {
            domainNameToString(domainNameDNS)
        } catch (ex: Exception) {
            null
        }
        if (domainName == null) {
            errors++
        } else {
            val domainNameString = domainName.dropLast(1).joinToString(".")

            if (sub == 0.toUByte()) {
                val stat = stats.get(domainNameString)
                if (stat != null) {
                    stat.times = updateTimes(stat.times, unixTimeStamp)
                    val currentPeak =
                        stat.times.size.toDouble() / windowSeconds.toDouble()
                    if (currentPeak > stat.peak) {
                        stat.peak = currentPeak
                    }
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
                    stats.put(
                        domainNameString,
                        Stats(
                            1,
                            validatedAmount,
                            listOf(unixTimeStamp),
                            1.toDouble() / windowSeconds.toDouble()
                        )
                    )
                }
            }

            if (index == 0) {
                firstTimeStamp = unixTimeStamp
            }
            if (unixTimeStamp > lastTimestamp) {
                lastTimestamp = unixTimeStamp
            }

            if (validated) {
                validationSuccess++
            }
            allTimes = updateTimes(allTimes, unixTimeStamp)
            val currentPeak =
                allTimes.size.toDouble() / windowSeconds.toDouble()
            if (currentPeak > allPeak) {
                allPeak = currentPeak
            }

        }
        if (unixTimeStamp < previousTime) {
            notInOrder++
        } else {
            previousTime = unixTimeStamp
        }
        return@readFileWithProgress dataLength + length.toInt() + 1
    }
    val out = File("out.csv")
    out.writeText("")
    out.appendText("Failed: $errors\n")
    out.appendText("Not in order: $notInOrder\n")
    out.appendText("Total amount of verifications: $totalLoops\n")
    out.appendText("Successfully validated: $validationSuccess\n")
    out.appendText("First validation: $firstTimeStamp\n")
    out.appendText("Last validation: $lastTimestamp\n")
    out.appendText("Validations per second in $windowSeconds seconds: $allPeak\n")
    val time = lastTimestamp - firstTimeStamp
    val averagePS = totalLoops.toDouble() / time.toDouble()
    out.appendText("Average validations per second: $averagePS\n")
    stats.toList().sortedBy { (domain, _) -> domain }.forEach { (domain, stat) ->
        out.appendText("$domain,${stat.validations},${stat.successfullValidations},${stat.peak}\n")
    }
}
