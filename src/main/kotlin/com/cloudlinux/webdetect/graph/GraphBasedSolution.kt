package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.DataContext
import com.cloudlinux.webdetect.MutableMap
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.graph.bfs.BfsBasedSolution
import com.cloudlinux.webdetect.graph.grouping.MergeAppVersionsWithSameChecksumsTask

private const val undetectedOutputPath = "undetected"
private const val matricesOutputPath = "matrices"

data class GraphBasedSolutionResult(
    val avDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    val definedAvDict: MutableMap<AppVersion, AppVersionGraphEntry>,
    val undefinedAvDict: Map<AppVersion, AppVersionGraphEntry>
)

fun graphBasedSolution(
    pooledCtx: DataContext,
    sufficientChecksumsRange: IntProgression
): GraphBasedSolutionResult {
    val avDict = createGraph(
        pooledCtx.checksumToAppVersions.filter { (_, v) -> v.mapTo(MutableSet()) { it.apps().single() }.size == 1 },
        pooledCtx.appVersions.keys
    )
    pooledCtx.cleanup()

    MergeAppVersionsWithSameChecksumsTask(avDict, MutableMap()).process()

    val definedAvDict = BfsBasedSolution(avDict, sufficientChecksumsRange).process()
    val undetected = avDict - definedAvDict.keys

    return GraphBasedSolutionResult(
        avDict,
        definedAvDict,
        undetected
    )
}