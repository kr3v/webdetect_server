package kr3v.webdetect.graph

import kr3v.webdetect.AppVersion
import kr3v.webdetect.graph.grouping.Matrix
import java.io.PrintWriter
import java.time.ZonedDateTime

fun statsGraph(
    definedAvDict: Map<AppVersion, AppVersionGraphEntry>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    max: Int
) {
    println("${ZonedDateTime.now()}: printing stats")
    println("Result: ${definedAvDict.size}/${avDict.size}")
    println("Stats:")
    definedAvDict.values
        .groupBy { it.checksums.size.coerceAtMost(max) }
        .mapValues { (_, v) -> v.size }
        .entries
        .sortedBy { it.key }
        .forEach { (k, u) -> println("$k -> $u") }
    println(avDict.values.sumBy { it.checksums.size.coerceAtMost(max) }.toDouble() / avDict.size.toDouble())
    println()
}

fun writeUndetected(
    undetected: Collection<AppVersion>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    writer: PrintWriter = PrintWriter(System.out)
) {
    println("${ZonedDateTime.now()}: writing undetected")
    if (undetected.isEmpty()) return
    writer.println("Not defined app-versions: ${undetected.size}")
    undetected
        .sortedBy(AppVersion::toString)
        .forEach {
            val e = avDict[it]
            writer.println("$it, checksums: ${e!!.checksums.size}")
        }
    writer.flush()
    println("${ZonedDateTime.now()}: written undetected")
}

fun writeSimilarityMatrices(
    m: Map<List<String>, Pair<List<AppVersionGraphEntry>, Matrix>>,
    writer: PrintWriter = PrintWriter(System.out)
) {
    if (m.isEmpty()) return
    writer.println("Similarity matrices count: ${m.size}")
    m.forEach { (k, v) ->
        val (header, matrix) = v
        writer.println("Matrix for $k")
        writeMatrix(header, matrix, writer)
        writer.println()
    }
    writer.flush()
}

private fun writeMatrix(
    header: List<AppVersionGraphEntry>,
    matrix: Matrix,
    writer: PrintWriter
) {
    val len = header.map { it.key.versions().toString().length }.max()!! + 2
    val paddingFormat = "%-$len"
    val stringFormat = paddingFormat + "s"
    val intFormat = paddingFormat + "d"
    val floatFormat = "$paddingFormat.2f"

    fun PrintWriter.printHeaderEntry(it: AppVersionGraphEntry) {
        printf(stringFormat, it.key.versions().toString())
    }

    // header
    writer.printf(stringFormat, "")
    header.forEach { writer.printHeaderEntry(it) }
    writer.println()

    //matrix
    for (i in header.indices) {
        writer.printHeaderEntry(header[i])
        for (j in header.indices) {
            writer.printf(floatFormat, matrix[i][j].toDouble() / header[i].checksums.size.toDouble())
        }
        writer.println()
    }
    writer.println()
    writer.flush()
}