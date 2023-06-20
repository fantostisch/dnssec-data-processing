import java.io.File

data class KeyStats(var total: Int, var duplicates: Int)

private fun analyzeZoneFile(file: File): Pair<String, Map<String, KeyStats>> {
    val zone = file.name.removeSuffix(".txt")
    val keys = mutableMapOf<String, KeyStats>()
    var previousSignedRecordType: String? = null
    file.forEachLine { l ->
        fun countRecord(): String? {
            val split = l.split("\t").filter { s -> s.isNotEmpty() }
            val type = split.getOrNull(3) ?: return null
            if (type.uppercase() != "RRSIG") {
                return null
            }
            val record = split.getOrNull(4) ?: return null
            val recordSplit = record.split(" ").filter { s -> s.isNotEmpty() }
            val signedRecordType = recordSplit.getOrNull(0) ?: return null
            val keyID = recordSplit.getOrNull(6) ?: return null
            val zoneStats = keys[keyID]
            if (zoneStats != null) {
                zoneStats.total = zoneStats.total + 1
                if (previousSignedRecordType == signedRecordType) {
                    zoneStats.duplicates = zoneStats.duplicates + 1
                }
            } else {
                keys[keyID] = KeyStats(1, 0)
            }
            return signedRecordType
        }

        val signedRecordType = countRecord()
        if (signedRecordType != null) {
            previousSignedRecordType = signedRecordType
        } else {
            previousSignedRecordType = null
        }
    }
    return Pair(zone, keys)
}

data class ZoneStat(val keys: Map<String, KeyStats>, val total: Int, val duplicates: Int, val necessarySignatures: Int)

fun keysToZoneStat(keys: Map<String, KeyStats>): ZoneStat {
    val total = keys.values.map { zoneStats -> zoneStats.total }.sum()
    val duplicates = keys.values.map { zoneStats -> zoneStats.duplicates }.sum()
    val necessarySignatures = total - duplicates
    return ZoneStat(keys, total, duplicates, necessarySignatures)
}

suspend fun analyzeZoneFiles(dirPath: String): List<Pair<String, ZoneStat>> {
    val dir = File(dirPath)

    return if (dir.isDirectory) {
        val files = dir.listFiles()!!.filter { f -> f.isFile }
        files.pmap { f -> analyzeZoneFile(f) }
    } else {
        listOf(analyzeZoneFile(dir))
    }.map { (zone, keys) -> Pair(zone, keysToZoneStat(keys)) }
}

suspend fun printZoneFiles(dirPath: String) {
    val result = analyzeZoneFiles(dirPath)
    println("Zone, Total, Necessary signatures, Key ID, Signatures, Already signed")
    result.sortedBy { r -> r.first }.forEach { (zone, zoneStat) ->
        print("${zone},${zoneStat.total},${zoneStat.necessarySignatures}")
        zoneStat.keys.toList().sortedByDescending { (_, zoneStats) -> zoneStats.total }
            .forEach { (key, zoneStat) ->
                print(",$key,${zoneStat.total},${zoneStat.duplicates}")
            }
        print('\n')
    }
}
