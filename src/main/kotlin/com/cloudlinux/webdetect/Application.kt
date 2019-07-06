package com.cloudlinux.webdetect

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import kotlin.math.min

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen database output")
    val `in`: String = args[0]
    val out: String = args[1]

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

    GraphTaskContext(avDict).process()

    println("Result: ${avDict.count { (_, v) -> v.checksums.all { it.appVersions.size == 1 } } - avDict.count { (_, v) -> v.checksums.size == 0 }}/${avDict.size}")
    println("Stats:")
    avDict.values
        .groupBy { min(5, it.checksums.size) }
        .mapValues { (_, v) -> v.size }
        .toSortedMap()
        .forEach { t, u -> println("$t -> $u") }

    OBJECT_MAPPER.writeValue(
        File(out),
        avDict
            .values
            .map {
                it.checksums
                    .sortedBy { cs -> cs.dependsOn.size }
                    .take(5)
            }
            .flatten()
            .associate {
                it.key.asHexString() to mapOf(
                    "av" to it.appVersions.single().key,
                    "deps" to it.dependsOn.map { av -> av.key }
                )
            }
    )
}