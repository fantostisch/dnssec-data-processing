import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import java.io.InputStream
import java.lang.Exception
import java.net.IDN

private fun exit(message: String) {
    System.err.println(message)
    exitProcess(1)
}

private fun printHelp() {
    exit(
        "Usage: anonymize FILE_LOCATION\n" +
                "or: analyze FILE_LOCATION (only works on anonymized files)\n" +
                "or: zone FOLDER_LOCATION [filter]\n"
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
    val includeSub = args.getOrNull(2) == "all"
    val filterDuplicateSignatures = args.getOrNull(2) == "filter"
    when (args[0]) {
        "analyze" -> analyze(fileLocation, includeSub)
        "anonymize" -> anonymize(fileLocation)
        "aa" -> {
            anonymize(fileLocation)
            analyze("$fileLocation.$anonSuffix", includeSub)
        }

        "zone" -> analyzeZoneFiles(fileLocation, filterDuplicateSignatures)
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
