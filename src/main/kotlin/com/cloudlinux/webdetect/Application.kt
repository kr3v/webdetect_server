package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.bloomfilter.bloomFilterBasedSolution
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.Optional

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
    pooledCtx.pool.cleanup()

    bloomFilterBasedSolution(pooledCtx, File(detect.get()).readLines())
//    graphBasedSolution(pooledCtx, Optional.of("$out.json"), Optional.of("$out.ldb"))
}

private fun processByDb(pathToShaList: String, pathToDb: String) = processByDb(
    pathToShaList,
    OBJECT_MAPPER.readValue<Map<String, Map<String, Any>>>(File(pathToDb))
)

private fun processByDb(pathToShaList: String, definedAv: Map<String, Any>) {
    println("Additional arg passed, checking $pathToShaList...")
    val lines = File(pathToShaList)
    lines.forEachLine { line ->
        if (line in definedAv) {
            println(definedAv[line])
        }
    }
}
