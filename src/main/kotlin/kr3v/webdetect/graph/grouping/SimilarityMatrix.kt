package kr3v.webdetect.graph.grouping

import kr3v.webdetect.AppVersion
import kr3v.webdetect.graph.AppVersionGraphEntry
import kr3v.webdetect.util.countIntersects

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
            d[i][j] = countIntersects(vi.checksums, vj.checksums)
        }
    }
    return d
}
