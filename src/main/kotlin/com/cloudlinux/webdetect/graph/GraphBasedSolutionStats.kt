package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.graph.grouping.Matrix
import java.io.PrintWriter

fun <C : ChecksumKey<C>> statsGraph(
    definedAvDict: Map<AppVersion, AppVersionGraphEntry<C>>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry<C>>,
    max: Int,
    run: Int
) {
    println("Result: ${definedAvDict.size}/${avDict.size}")
    println("Stats $run:")
    definedAvDict.values
        .groupBy {
            val exclusive = it.checksums.size
            val released = it.released.size
            exclusive.coerceAtMost(max) to (exclusive + released).coerceAtMost(max)
        }
        .mapValues { (_, v) -> v.size }
        .toSortedMap(compareBy({ it.first }, { it.second }))
        .forEach { (k, u) -> println("$k -> $u") }
    println(avDict.values.sumBy { it.checksums.size.coerceAtMost(max) }.toDouble() / avDict.size.toDouble())
    println()
}

fun <C : ChecksumKey<C>> writeUndetected(
    undetected: Collection<AppVersion>,
    avDict: MutableMap<AppVersion, AppVersionGraphEntry<C>>,
    writer: PrintWriter = PrintWriter(System.out)
) {
    if (undetected.isEmpty()) return
    writer.println("Not defined app-versions: ${undetected.size}")
    undetected
        .sortedBy(AppVersion::toString)
        .forEach {
            val e = avDict[it]
            writer.println("$it, checksums: ${e!!.checksums.size}")
        }
    writer.flush()
}

fun <C : ChecksumKey<C>> writeSimilarityMatrices(
    m: Map<List<String>, Pair<List<AppVersionGraphEntry<C>>, Matrix>>,
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

private fun <C : ChecksumKey<C>> writeMatrix(
    header: List<AppVersionGraphEntry<C>>,
    matrix: Matrix,
    writer: PrintWriter
) {
    val len = header.map { it.key.versions().toString().length }.max()!! + 2
    val paddingFormat = "%-$len"
    val stringFormat = paddingFormat + "s"
    val intFormat = paddingFormat + "d"
    val floatFormat = "$paddingFormat.2f"

    fun PrintWriter.printHeaderEntry(it: AppVersionGraphEntry<C>) {
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