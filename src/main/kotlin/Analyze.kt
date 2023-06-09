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
    var allTimes = listOf<UInt>()
    var allPeak = 0.toDouble()
    var previousTime = 0u
    var notInOrder = 0
    val totalLoops = readFileWithProgress(fileLocation) { inputStream, index ->
        val (readAmount, ud) = readUData(inputStream, index)
        val domainNameString = ud.domainName.dropLast(1).joinToString(".")

        if (includesub || !ud.sub) {
            val stat = stats.get(domainNameString)
            if (stat != null) {
                stat.times = updateTimes(stat.times, ud.unixTimeStamp)
                val currentPeak =
                    stat.times.size.toDouble() / windowSeconds.toDouble()
                if (currentPeak > stat.peak) {
                    stat.peak = currentPeak
                }
                stat.validations++
                if (ud.validated) {
                    stat.successfullValidations++
                }
            } else {
                val validatedAmount = if (ud.validated) {
                    1
                } else {
                    0
                }
                stats.put(
                    domainNameString,
                    Stats(
                        1,
                        validatedAmount,
                        listOf(ud.unixTimeStamp),
                        1.toDouble() / windowSeconds.toDouble()
                    )
                )
            }
        }

        if (index == 0) {
            firstTimeStamp = ud.unixTimeStamp
        }
        if (ud.unixTimeStamp > lastTimestamp) {
            lastTimestamp = ud.unixTimeStamp
        }

        if (ud.validated) {
            validationSuccess++
        }
        allTimes = updateTimes(allTimes, ud.unixTimeStamp)
        val currentPeak =
            allTimes.size.toDouble() / windowSeconds.toDouble()
        if (currentPeak > allPeak) {
            allPeak = currentPeak
        }

        if (ud.unixTimeStamp < previousTime) {
            notInOrder++
        } else {
            previousTime = ud.unixTimeStamp
        }
        return@readFileWithProgress readAmount
    }
    val out = File("out.csv")
    out.writeText("")
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
