package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.graph.GraphBasedSolutionSerializer
import com.cloudlinux.webdetect.graph.SerializableChecksumsChooser
import com.cloudlinux.webdetect.graph.graphBasedSolution
import com.cloudlinux.webdetect.graph.statsGraph
import com.cloudlinux.webdetect.graph.writeUndetected
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.time.ZonedDateTime
import java.util.Optional

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen <csv path> <output path>")
    val `in`: String = args[0]
    val out: String = args[1]

    val webdetectCtx = buildContextByCsv(`in`)
    webdetectCtx.pool.cleanup()

    graphSolution(
        webdetectCtx,
        out
    )
}

private const val undetectedOutputPath = "undetected"
private const val matricesOutputPath = "matrices"

private fun graphSolution(ctx: WebdetectContext, out: String) {
    val max = 5
    val (avDict, csDict, definedAvDict, undetected) = graphBasedSolution(ctx)

    println("${ZonedDateTime.now()}: printing stats")
    statsGraph(definedAvDict, avDict, max, -1)

    println("${ZonedDateTime.now()}: serializing")
    val serializableChecksumsChooser = SerializableChecksumsChooser(ctx.pathToCsv, csDict, avDict, max)
    GraphBasedSolutionSerializer(avDict, definedAvDict, serializableChecksumsChooser).serialize(
        Optional.of("$out.json"),
        Optional.of("$out.ldb")
    )

    println("${ZonedDateTime.now()}: finding undetected")
    writeUndetected(undetected.keys, avDict, PrintWriter(FileOutputStream(File(undetectedOutputPath))))
}
