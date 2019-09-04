package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumKey
import com.cloudlinux.webdetect.util.countIntersects

typealias Matrix = Array<IntArray>

fun <C : ChecksumKey<C>> similarityMatrices(
    nonDefinedAvDict: Map<AppVersion, AppVersionGraphEntry<C>>
): Map<List<String>, Pair<List<AppVersionGraphEntry<C>>, Matrix>> = nonDefinedAvDict
    .values
    .groupBy { v -> v.key.apps() }
    .mapValues { (_, v) -> v to similarityMatrixImpl(v) }

private fun <C : ChecksumKey<C>> similarityMatrixImpl(list: List<AppVersionGraphEntry<C>>): Matrix {
    val d = Array(list.size) { IntArray(list.size) }
    for ((i, vi) in list.withIndex()) {
        for ((j, vj) in list.withIndex()) {
            d[i][j] = countIntersects(vi.checksums, vj.checksums)
        }
    }
    return d
}
