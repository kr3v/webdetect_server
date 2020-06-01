package kr3v.webdetect.graph

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kr3v.webdetect.AppVersion
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.FMutableSet
import kr3v.webdetect.util.intForEach
import kr3v.webdetect.util.maxBy
import kr3v.webdetect.util.minBy
import java.time.ZonedDateTime

var HasIntProperties.localIndex
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

typealias AVGE = AppVersionGraphEntry
typealias CGE = ChecksumGraphEntry

class ChecksumBalancer(val avDict: Map<AppVersion, AVGE>) {
    private val values = avDict.values.toList()
    private val topSortIndices: LongArray
    private val usedTopSortIndices: LongOpenHashSet
    private val graph: Graph
    private val inverseGraph: Graph

    private var optimized = 0

    init {
        println("${ZonedDateTime.now()}: balancing checksums started")

        values.forEachIndexed { index, av ->
            av.properties = IntArray(1)
            av.localIndex = index
        }
        topSortIndices = LongArray(values.size) { 0L }
        usedTopSortIndices = LongOpenHashSet(values.size)
        graph = buildGraph(values)
        inverseGraph = inverse(graph)

        topologicalSort(graph, topSortIndices)
        usedTopSortIndices.addAll(topSortIndices.toList())
    }

    fun process() {
        (1..200).takeWhile {
            val before = optimized
            values
                .filter { it.released.size > 0 }
                .groupByTo(FMutableMap()) { it.checksums.size }
                .entries
                .sortedBy { it.key }
                .forEach { (_, v) -> v.tryOptimize() }
            // continue iterating until nothing can be optimized or run out of iterations
            before != optimized
        }
        verifyAvDictConsistency()
        println("${ZonedDateTime.now()}: balancing checksums done")
    }

    private fun Iterable<AVGE>.tryOptimize() = forEach { rhs ->
        rhs.released
            .groupByTo(FMutableMap()) { cs -> cs.appVersions.single() }
            .entries
            .tryToReverse(rhs)
    }

    private fun Iterable<Map.Entry<AVGE, List<CGE>>>.tryToReverse(rhs: AVGE) = forEach { (lhs, checksumList) ->
        val honestReverse = lhs.checksums.size - checksumList.size >= rhs.checksums.size + checksumList.size
        if (honestReverse && willGraphBeAcyclicAfterReverse(lhs, rhs)) {
            val dependsOn = checksumList.flatMapTo(FMutableSet()) { it.dependsOn }
            dependsOn -= lhs
            dependsOn -= rhs
            checksumList.forEach { reverseChecksum(lhs, rhs, it) }
            reverseGraph(lhs, rhs, dependsOn)
            optimized++
        }
    }

    private fun willGraphBeAcyclicAfterReverse(lhs: AVGE, rhs: AVGE): Boolean {
        val lhsIndex = lhs.localIndex
        val rhsIndex = rhs.localIndex
        val lhsTsIndex = topSortIndices[lhsIndex]
        return inverseGraph[rhsIndex].none { idx -> topSortIndices[idx] < lhsTsIndex }
    }

    //       cs
    // lower -> higher
    private fun reverseChecksum(lower: AVGE, higher: AVGE, cs: CGE) {
        cs.dependsOn += lower
        cs.dependsOn -= higher
        cs.appVersions += higher
        cs.appVersions -= lower
        lower.released += cs
        lower.checksums -= cs
        higher.checksums += cs
        higher.released -= cs
    }

    private fun reverseGraph(lower: AVGE, higher: AVGE, dependsOn: Collection<AVGE>) {
        val lowerIdx = lower.localIndex
        val higherIdx = higher.localIndex
        val doIndices = IntArrayList(dependsOn.size).apply { dependsOn.forEach { add(it.localIndex) } }

        updateGraph(lowerIdx, lower, higherIdx, doIndices)
        updateTopSortIndices(higherIdx)
    }

    private fun updateGraph(lowerIdx: Int, lower: AVGE, higherIdx: Int, doIndices: IntArrayList) {
        graph[lowerIdx].clear()
        lower.checksums.forEach { cs -> cs.dependsOn.forEach { av -> graph[lowerIdx].add(av.localIndex) } }

        graph[higherIdx].addAll(doIndices)
        graph[higherIdx].add(lowerIdx)

        inverseGraph[lowerIdx].add(higherIdx)
        inverseGraph[higherIdx].remove(lowerIdx)
        doIndices.intForEach {
            inverseGraph[it].add(higherIdx)
            if (it !in graph[lowerIdx])
                inverseGraph[it].remove(lowerIdx)
        }
    }

    private fun updateTopSortIndices(higherIdx: Int) {
        val max = inverseGraph[higherIdx].minBy(Long.MAX_VALUE, topSortIndices::get)
        val min = graph[higherIdx].maxBy(0L, topSortIndices::get)

        val idx = tryToFindAvailableTopologicalIndex(min + 1, max - 1)
        if (idx >= max || idx <= min || idx in usedTopSortIndices) {
            topologicalSort(graph, topSortIndices)
            usedTopSortIndices.clear()
            usedTopSortIndices.addAll(topSortIndices.toList())
        } else {
            usedTopSortIndices.add(idx)
            usedTopSortIndices.remove(topSortIndices[higherIdx])
            topSortIndices[higherIdx] = idx
        }
    }

    private fun tryToFindAvailableTopologicalIndex(min: Long, max: Long): Long {
        var range = min..max
        var idx = middle(range)
        while (idx in usedTopSortIndices && idx < max) {
            range = (idx + 1)..max
            idx = middle(range)
        }
        range = min..max
        idx = middle(range)
        while (idx in usedTopSortIndices && idx > min) {
            range = min..idx
            idx = middle(range)
        }
        return idx
    }

    private fun middle(range: LongRange) = (range.last - range.first) / 2 + range.first

    private fun verifyAvDictConsistency() {
        val pureGraph = buildGraph(values)
        val errors = ArrayList<String>()

        if (graph != pureGraph) errors.add("Graph is different from inverse pure graph")
        if (inverseGraph != inverse(pureGraph)) errors.add("Inverse graph is different from pure graph")

        validateGraphIsAcyclic(pureGraph, errors)

        if (errors.isNotEmpty()) {
            throw Exception(errors.toString())
        }
    }
}

