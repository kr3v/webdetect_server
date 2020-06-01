package kr3v.webdetect

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kr3v.webdetect.graph.graphSolution

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen <csv path> <output path>")
    val `in`: String = args[0]
    val out: String = args[1]

    val maxChecksums = 5
    graphSolution(`in`, out, maxChecksums)
}
