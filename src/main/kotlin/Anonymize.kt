import java.io.File
import java.lang.Exception
import java.net.IDN

private fun domainNameToList(byteArray: ByteArray): List<ByteArray> {
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

private fun byteArrayListEquals(a: List<ByteArray>, b: List<ByteArray>): Boolean {
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

private fun isPartOf(domain: List<ByteArray>, suffix: List<ByteArray>): Boolean {
    val from = domain.size - suffix.size
    return if (from < 0) {
        false
    } else {
        byteArrayListEquals(domain.subList(from, domain.size), suffix)
    }
}

private fun getPublicSuffixes(): List<String> {
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

private fun getSuffixWithoutRoot(
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

private fun getSuffix(
    domain: List<ByteArray>,
    suffixes: List<Suffix>
): Pair<Boolean, List<ByteArray>>? {
    return getSuffixWithoutRoot(
        domain.dropLast(1),
        suffixes
    )?.let { (sub, domain) -> Pair(sub, domain + ByteArray(0)) }
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
