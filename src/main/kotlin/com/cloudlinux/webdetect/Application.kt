package com.cloudlinux.webdetect

import com.cloudlinux.webdetect.bloomfilter.BloomFilterSolutionParameters
import com.cloudlinux.webdetect.bloomfilter.bloomFilterBasedSolution
import com.cloudlinux.webdetect.graph.GraphBasedSolutionSerializer
import com.cloudlinux.webdetect.graph.graphBasedSolution
import com.cloudlinux.webdetect.graph.statsBfs
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.Optional

val OBJECT_MAPPER = jacksonObjectMapper()

fun main(args: Array<String>) {
    if (args.size < 2) throw Exception("Usage: webdetect_gen database output")
    val `in`: String = args[0]
    val out: String = args[1]
    val detect = args.drop(2)

    val pooledCtx = DataContext()
    read(`in`, "\t") { (app, version, hash) -> pooledCtx.doPooling(app, version, hash) }
    pooledCtx.pool.cleanup()

    val max = 5
    val min = 1
    val (_, definedAvDict, _) = graphBasedSolution(
        pooledCtx,
        max downTo min
    )

    find(definedAvDict, detect)
}

private fun bloomFilterSolution(
    pooledCtx: DataContext,
    detect: Optional<String>
) {
    bloomFilterBasedSolution(
        BloomFilterSolutionParameters(
            bloomFilterFalsePositiveProbability = 0.01,
            leafsPerNode = 2,
            matchingThreshold = 0.5,
            bloomFilterMinimumSize = 100
        ),
        pooledCtx,
        File(detect.get()).readLines()
    )
}

private fun graphSolution(pooledCtx: DataContext, out: String) {
    val max = 5
    val min = 1
    val (avDict, definedAvDict, _) = graphBasedSolution(
        pooledCtx,
        max downTo min
    )
    statsBfs(definedAvDict, avDict, max)
    GraphBasedSolutionSerializer(avDict, definedAvDict, max).serialize(
        Optional.of("$out.json"),
        Optional.of("$out.ldb")
    )
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
