import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import java.lang.Exception

fun verificationSucceeded(byte: Byte): Boolean {
    return byte == 1.toByte()
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

suspend fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exit("Please specify log file as argument.")
    }

    val suffixes = getPublicSuffixes()

    val stats = mutableMapOf<String, Stats>()

    val logFile = File(args[0])
    val logFileSize = logFile.length()
    val inputStream = logFile.inputStream()
    var firstTimeStamp = 0u
    var lastTimestamp = 0u
    var index = 0
    var validationSuccess = 0
    var readBytes = 0
    var errors = 0
    var allTimes = listOf<UInt>()
    var allPeak = 0.toDouble()
    coroutineScope {
        val progressJob = launch {
            while (true) {
                val progress = readBytes.toDouble() / logFileSize.toDouble() * 100
                val progressString = "%.1f".format(progress)
                println("$progressString%")
                delay(1000)
            }
        }
        launch {
            while (inputStream.available() > 0) {
                val dataLength = 8
                val data = inputStream.readNBytes(dataLength)
                val unixTimeStamp = data.getUIntAt(0)
                val algo = data.get(5).toUByte()
                val validated = verificationSucceeded(data.get(6))
                val length = data.get(7).toUByte()

                val domainNameDNS = inputStream.readNBytes(length.toInt())
                assert(inputStream.read() == '\n'.code)

                val domainName = try {
                    domainNameToString(domainNameDNS)
                } catch (ex: Exception) {
                    null
                }
                if (domainName == null) {
                    errors++
                } else {
                    val domainNameString = domainName.dropLast(1).joinToString(".")

                    if (domainName.size <= 2 || suffixes.contains(domainNameString)) {
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
                    lastTimestamp = unixTimeStamp

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
                index++
                readBytes += dataLength + length.toInt() + 1
            }
            progressJob.cancel()
            inputStream.close()
        }
    }
    val out = File("out.csv")
    out.writeText("")
    out.appendText("Failed: $errors\n")
    out.appendText("Total amount of verifications: $index\n")
    out.appendText("Successfully validated: $validationSuccess\n")
    out.appendText("First validation: $firstTimeStamp\n")
    out.appendText("Last validation: $lastTimestamp\n")
    out.appendText("Validations per second in $windowSeconds seconds: $allPeak\n")
    val time = lastTimestamp - firstTimeStamp
    val averagePS = index.toDouble() / time.toDouble()
    out.appendText("Average validations per second: $averagePS\n")
    stats.toList().sortedBy { (domain, _) -> domain }.forEach { (domain, stat) ->
        out.appendText("$domain, ${stat.validations}, ${stat.successfullValidations},${stat.peak}\n")
    }
    println("Done")
}
