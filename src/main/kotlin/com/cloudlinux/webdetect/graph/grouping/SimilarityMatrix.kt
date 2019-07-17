package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry

typealias Matrix = Array<IntArray>

fun similarityMatrices(
    nonDefinedAvDict: Map<AppVersion, AppVersionGraphEntry>
): Map<List<String>, Pair<List<AppVersionGraphEntry>, Matrix>> = nonDefinedAvDict
    .values
    .groupBy { v -> v.key.apps() }
    .mapValues { (_, v) -> v to similarityMatrixImpl(v) }

private fun similarityMatrixImpl(list: List<AppVersionGraphEntry>): Matrix {
    val d = Array(list.size) { IntArray(list.size) }
    for ((i, vi) in list.withIndex()) {
        for ((j, vj) in list.withIndex()) {
            d[i][j] = (vi.checksums intersect vj.checksums).size
        }
    }
    return d
}
