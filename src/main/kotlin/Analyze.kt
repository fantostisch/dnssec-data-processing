import java.io.File
import java.lang.Exception

data class Stats(
    var validations: Int,
    var successfullValidations: Int,
    var times: List<UInt>,
    var peak: Double
)

private val windowSeconds = 30u

private fun updateTimes(times: List<UInt>, unixTimeStamp: UInt): List<UInt> {
    val newTimes = times.dropWhile { time -> time < unixTimeStamp - windowSeconds }
    return newTimes + unixTimeStamp
}

suspend fun analyze(fileLocation: String, includesub: Boolean) {
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

            if (includesub || sub == 0.toUByte()) {
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
