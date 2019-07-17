package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.bfs.BfsBasedSolution
import com.cloudlinux.webdetect.graph.createGraph
import com.cloudlinux.webdetect.graph.grouping.Matrix
import com.cloudlinux.webdetect.graph.grouping.MergeSameAppVersionsTask
import com.cloudlinux.webdetect.graph.grouping.similarityMatrices
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.Optional
import kotlin.math.min

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen database output")
    val `in`: String = args[0]
    val out: String = args[1]
    val detect = if (args.size == 3) Optional.of(args[2])
    else Optional.empty()

    val pooledCtx = PooledCtx()
    read(
        `in`,
        "\t",
        { it },
        { (app, version, hash) -> pooledCtx.doPooling(app, version, hash) }
    )
    Pool.appVersions.clear()
    Pool.appVersions.trim()
    Pool.checksums.clear()
    Pool.checksums.trim()
    Pool.strings.clear()
    Pool.strings.trim()

    val (avDict, csDict) = createGraph(pooledCtx.checksumToAppVersions, pooledCtx.appVersions)
    pooledCtx.checksumToAppVersions.clear()
    pooledCtx.checksumToAppVersions.trim()
    pooledCtx.appVersions.clear()
    pooledCtx.appVersions.trim()

    val MAX = 5
    val MIN = 1
    val definedAvDict = BfsBasedSolution(avDict, MAX downTo MIN).process()

    statsBfs(definedAvDict, avDict, MAX)

    MergeSameAppVersionsTask(/*csDict, */avDict, definedAvDict).process()
    definedAvDict += BfsBasedSolution(avDict, MAX downTo MIN).process()

    statsBfs(definedAvDict, avDict, MAX)
    writeToOut(definedAvDict, MAX, out)

    val undetected = avDict - definedAvDict.keys
    writeUndetected(undetected.keys, avDict, PrintWriter(FileOutputStream(File("undetected"))))

    val matrices = similarityMatrices(undetected)
    writeSimilarityMatrices(matrices, PrintWriter(FileOutputStream(File("matrices"))))
}

private fun statsBfs(
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

private fun statsPq(
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

fun processByDb(pathToShaList: String, pathToDb: String) {
    val definedAv = OBJECT_MAPPER.readValue<Map<String, Map<String, Any>>>(File(pathToDb))
    println("Additional arg passed, checking $pathToShaList...")
    val lines = File(pathToShaList)
    lines.forEachLine { line ->
        val cs = line
        if (cs in definedAv) {
            println(definedAv[cs])
        }
    }
}

fun processByDb(pathToShaList: String, definedAv: Map<Checksum, Map<String, Any>>) {
    println("Additional arg passed, checking $pathToShaList...")
    val lines = File(pathToShaList)
    lines.forEachLine { line ->
        val cs = line.asChecksumLong()
        if (cs in definedAv) {
            println(definedAv[cs])
        }
    }
}

private fun writeToOut(
    r: MutableMap<AppVersion, AppVersionGraphEntry>,
    max: Int,
    out: String
): Map<Checksum, Map<String, Any>> {
    val definedAv = r
        .values
        .map {
            it.checksums
                .sortedBy { cs -> cs.dependsOn.size }
                .take(max)
        }
        .flatten()
        .associate {
            it.key to mapOf(
                "av" to it.appVersions.single().key,
                "deps" to it.dependsOn.map { av -> av.key }
            )
        }

    OBJECT_MAPPER.writeValue(
        File(out),
        definedAv
    )

    return definedAv
}