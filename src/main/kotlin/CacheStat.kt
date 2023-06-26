import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class CacheStat(val time: Date, val cacheHits: Double, val cacheMisses: Double)

private fun parseNumber(number: String): Double {
    try {
        if (!number.contains('K')) {
            return number.toDouble()
        }
        val numberChars = number.takeWhile { c -> c != ' ' }
        assertOrExit(numberChars[1] == '.', "Could not parse number")
        return numberChars.toDouble() * 1000
    } catch (e: Exception) {
        throw IllegalArgumentException("Could not parse number: $number", e)
    }
}

fun parseQueryLoad(filePath: String): List<CacheStat> {
    val file = File(filePath)
    val simpleDatFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val cacheStatList = mutableListOf<CacheStat>()
    var first = true
    file.forEachLine { l ->
        if (first) {
            first = false
            if (l.startsWith("")) {
                return@forEachLine
            }
        }
        if (l.isEmpty()) {
            return@forEachLine
        }
        val split = l.split(",")
        if (split.size != 3) {
            throw IllegalArgumentException("Invalid line: '$l'")
        }
        val (timeString, cacheHString, cacheMString) = split
        val time = simpleDatFormat.parse(timeString)
        val cacheHits = parseNumber(cacheHString)
        val cacheMisses = parseNumber(cacheMString)
        cacheStatList.add(CacheStat(time, cacheHits, cacheMisses))
    }
    return cacheStatList
}
