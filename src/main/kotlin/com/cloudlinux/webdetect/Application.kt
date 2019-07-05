package com.cloudlinux.webdetect

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

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

    val result = findDefinedAppVersions(avDict, 5)
    (4 downTo 1).forEach {
        result += findDefinedAppVersions(avDict, it)
    }

    println("Result: ${result.size}/${avDict.size}")

    OBJECT_MAPPER.writeValue(
        File(out),
        result
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