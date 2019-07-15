package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.bfs.BfsBasedSolution
import com.cloudlinux.webdetect.graph.createGraph
import com.cloudlinux.webdetect.graph.pq.PriorityQueueBasedSolution
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.util.Optional
import kotlin.math.min

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen database output")
    val `in`: String = args[0]
    val out: String = args[1]
    val detect = if (args.size == 3) Optional.of(args[2])
    else Optional.empty()

    val (checksumToAppVersions, appVersions) = read(`in`)
    Pool.appVersions.clear()
    Pool.appVersions.trim()
    Pool.checksums.clear()
    Pool.checksums.trim()
    Pool.strings.clear()
    Pool.strings.trim()

    val avDict = createGraph(checksumToAppVersions, appVersions)
    checksumToAppVersions.clear()
    checksumToAppVersions.trim()
    appVersions.clear()
    appVersions.trim()

    val MAX = 5
    val MIN = 5
    val r = BfsBasedSolution(avDict, MAX downTo MIN).process()

    println("Result: ${r.size}/${avDict.size}")
    println("Stats:")
    r.values
        .groupBy { min(MAX, it.checksums.size) }
        .mapValues { (_, v) -> v.size }
        .toSortedMap()
        .forEach { t, u -> println("$t -> $u") }

    val definedAv = r
        .values
        .map {
            it.checksums
                .sortedBy { cs -> cs.dependsOn.size }
                .take(MAX)
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

//    val definedAv = OBJECT_MAPPER.readValue<Map<String, Map<String, Any>>>(File("db.json"))

//    val f = File("undetected").writer()
//    println()
//    (avDict.keys - r.keys)
//        .sortedBy { it.both }
//        .forEach { println("$it -> ${avDict[it]}") }
//    println()

    detect.ifPresent { pathToShaList ->
        println("Additional arg passed, checking $pathToShaList...")
        val lines = File(pathToShaList)
        lines.forEachLine { line ->
            val cs = line.asChecksumLong()
            if (cs in definedAv) {
                println(definedAv[cs])
            }
        }
    }
}