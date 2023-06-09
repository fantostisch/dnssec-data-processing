import java.io.File

fun analyzeZoneFile(
    file: File,
    filterDuplicateSignatures: Boolean
): Pair<String, Map<String, Int>> {
    val zone = file.name.removeSuffix(".txt")
    val keys = mutableMapOf<String, Int>()
    var previousSignature = false
    file.forEachLine { l ->
        val split = l.split("\t")
        val type = split.getOrNull(3) ?: return@forEachLine
        if (type.uppercase() != "RRSIG") {
            return@forEachLine
        }
        val record = split.getOrNull(4) ?: return@forEachLine
        val recordSplit = record.split(" ")
        val signedRecordType = recordSplit.getOrNull(0) ?: return@forEachLine
        val keyID = recordSplit.getOrNull(6) ?: return@forEachLine
        val amountOfSignatures = keys[keyID]
        if (amountOfSignatures != null) {
            keys[keyID] = amountOfSignatures + 1
        } else {
            keys[keyID] = 1
        }
    }
    return Pair(zone, keys)
}

suspend fun analyzeZoneFiles(dirPath: String, filterDuplicateSignatures: Boolean) {
    val dir = File(dirPath)

    val result = if (dir.isDirectory) {
        val files = dir.listFiles()!!.filter { f -> f.isFile }
        files.pmap { f -> analyzeZoneFile(f, filterDuplicateSignatures) }
    } else {
        listOf(analyzeZoneFile(dir, filterDuplicateSignatures))
    }
    result.sortedBy { r -> r.first }.forEach { r ->
        print(r.first)
        r.second.toList().sortedByDescending { (_, amount) -> amount }
            .forEach { (key, amount) ->
                print(",$key,$amount")
            }
        print('\n')
    }
}
