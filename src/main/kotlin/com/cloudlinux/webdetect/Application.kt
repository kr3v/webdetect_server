package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.bloomfilter.BloomFilterSolutionParameters
import com.cloudlinux.webdetect.bloomfilter.bloomFilterBasedSolution
import com.cloudlinux.webdetect.graph.GraphBasedSolutionSerializer
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
    val detect = args.drop(2)

    val webdetectCtx = buildContextFromCsv(`in`)

    graphSolution(
        webdetectCtx,
        out
    )
}

fun buildContextFromCsv(`in`: String): WebdetectContext {
    val webdetectCtx = WebdetectContext()
    println("${ZonedDateTime.now()}: $`in` processing started")
    read(
        path = `in`,
        separator = "\t",
        withoutPathHandler = { (app, version, hash) -> webdetectCtx.doPooling(app, version, hash) },
        withPathHandler = { (app, version, hash, _) -> webdetectCtx.doPooling(app, version, hash, null) }
    )
    println("${ZonedDateTime.now()}: $`in` processing done")
    webdetectCtx.pool.cleanup()
    return webdetectCtx
}

private fun bloomFilterSolution(
    webdetectCtx: WebdetectContext,
    detect: Optional<String>
) {
    bloomFilterBasedSolution(
        BloomFilterSolutionParameters(
            bloomFilterFalsePositiveProbability = 0.01,
            leafsPerNode = 2,
            matchingThreshold = 0.5,
            bloomFilterMinimumSize = 100
        ),
        webdetectCtx,
        File(detect.get()).readLines()
    )
}

private const val undetectedOutputPath = "undetected"
private const val matricesOutputPath = "matrices"

private fun graphSolution(webdetectCtx: WebdetectContext, out: String) {
    val max = 5
    val min = 1
    val (avDict, _, definedAvDict, undetected) = graphBasedSolution(webdetectCtx)

    println("${ZonedDateTime.now()}: printing stats")
    statsGraph(definedAvDict, avDict, max, -1)

    println("${ZonedDateTime.now()}: serializing")
    GraphBasedSolutionSerializer(avDict, definedAvDict, max).serialize(
        Optional.of("$out.json"),
        Optional.empty()
    )

    println("${ZonedDateTime.now()}: finding undetected")
    writeUndetected(undetected.keys, avDict, PrintWriter(FileOutputStream(File(undetectedOutputPath))))
}
