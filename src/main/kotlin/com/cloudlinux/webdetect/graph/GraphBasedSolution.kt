package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.PooledCtx
import com.cloudlinux.webdetect.graph.bfs.BfsBasedSolution
import com.cloudlinux.webdetect.graph.grouping.MergeAppVersionsWithSameChecksumsTask
import com.cloudlinux.webdetect.graph.grouping.similarityMatrices
import java.io.File
import java.util.Optional

private const val undetectedOutputPath = "undetected"
private const val matricesOutputPath = "matrices"

fun graphBasedSolution(
    pooledCtx: PooledCtx,
    jsonOut: Optional<String> = Optional.empty(),
    levelDbOut: Optional<String> = Optional.empty()
) {
    val (avDict, csDict) = createGraph(
        pooledCtx.checksumToAppVersions.filter { (_, v) -> v.mapTo(MutableSet()) { it.apps().single() }.size == 1 },
        pooledCtx.appVersions.keys
    )
    pooledCtx.cleanup()

    MergeAppVersionsWithSameChecksumsTask(avDict, MutableMap()).process()

    val MAX = 5
    val MIN = 1
    val definedAvDict = BfsBasedSolution(avDict, MAX downTo MIN).process()

    statsBfs(definedAvDict, avDict, MAX)
    GraphBasedSolutionSerializer(avDict, definedAvDict, MAX).serialize(jsonOut, levelDbOut)

    val undetected = avDict - definedAvDict.keys
    writeUndetected(undetected.keys, avDict, File(undetectedOutputPath).printWriter())

    val matrices = similarityMatrices(undetected)
    writeSimilarityMatrices(matrices, File(matricesOutputPath).printWriter())
}