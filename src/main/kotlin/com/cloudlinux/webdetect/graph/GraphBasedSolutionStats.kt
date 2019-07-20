package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.graph.grouping.Matrix
import java.io.PrintWriter
import kotlin.math.min

fun statsBfs(
    r: MutableMap<AppVersion, AppVersionGraphEntry>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    max: Int
) {
    println("Result: ${r.size}/${avDict.size}")
    println("Stats:")
    r.values
        .groupBy { min(max, it.checksums.size) }
        .mapValues { (_, v) -> v.size }
        .toSortedMap()
        .forEach { t, u -> println("$t -> $u") }
}

fun statsPq(
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    max: Int
) {
    println("Result: ${avDict.count { (_, v) -> v.checksums.size > 0 }}/${avDict.size}")
    println("Stats:")
    avDict.values
        .groupBy { min(max, it.checksums.size) }
        .mapValues { (_, v) -> v.size }
        .toSortedMap()
        .forEach { t, u -> println("$t -> $u") }
}

fun writeUndetected(
    undetected: Collection<AppVersion>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    writer: PrintWriter = PrintWriter(System.out)
) {
    if (undetected.isEmpty()) return
    writer.println("Not defined app versions: ${undetected.size}")
    undetected
        .sortedBy(AppVersion::toString)
        .forEach {
            val e = avDict[it]
            writer.println("$it, checksums: ${e!!.checksums.size}")
        }
    writer.flush()
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