package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.util.intForEach
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

typealias Graph = List<IntOpenHashSet>

private const val white = 0.toByte()
private const val gray = 1.toByte()
private const val black = 2.toByte()

fun buildGraph(list: Collection<AVGE>) = list.map(::buildGraphVertex)
private fun buildGraphVertex(it: AVGE) =
    it.checksums.flatMapTo(IntOpenHashSet()) { cs -> cs.dependsOn.map { av -> av.localIndex } }

fun inverse(graph: Graph): MutableList<IntOpenHashSet> {
    val inverseGraph = MutableList(graph.size) { IntOpenHashSet() }
    graph.forEachIndexed { lhs, values ->
        values.intForEach { rhs ->
            inverseGraph[rhs].add(lhs)
        }
    }
    return inverseGraph
}

fun topologicalSort(
    graph: Graph,
    topSortIndices: LongArray,
    color: ByteArray = ByteArray(graph.size) { white }
) {
    var nextIdx = 1L
    fun dfs(from: Int) {
        color[from] = gray
        graph[from].intForEach { to ->
            when (color[to]) {
                gray -> throw Exception()
                white -> dfs(to)
            }
        }
        topSortIndices[from] = nextIdx++ * (1 shl 30).toLong()
        color[from] = black
    }

    for (idx in graph.indices) {
        if (color[idx] == white) {
            dfs(idx)
        }
    }
}

fun validateGraphIsAcyclic(
    graph: List<IntOpenHashSet>,
    errors: ArrayList<String>,
    color: ByteArray = ByteArray(graph.size) { white }
) {
    fun dfs(from: Int) {
        color[from] = gray
        graph[from].intForEach { to ->
            when (color[to]) {
                gray -> errors.add("Graph is acyclic at from=$from to=$to")
                white -> dfs(to)
            }
        }
        color[from] = black
    }

    for (idx in graph.indices) {
        if (color[idx] == white) {
            dfs(idx)
        }
    }
}
