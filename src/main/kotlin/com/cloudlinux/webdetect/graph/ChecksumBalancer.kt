package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.util.intForEach
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.time.ZonedDateTime

var HasIntProperties.localIndex
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

typealias AVGE<C> = AppVersionGraphEntry<C>
typealias CGE<C> = ChecksumGraphEntry<C>

class ChecksumBalancer<C : ChecksumKey<C>>(
    val avDict: FMutableMap<AppVersion, AVGE<C>>,
    val csDict: FMutableMap<C, CGE<C>>,
    val max: Int
) {
    companion object {
        private const val white = 0.toByte()
        private const val gray = 1.toByte()
        private const val black = 2.toByte()
    }

    private val values = avDict.values.toList()

    private val color: ByteArray
    private val graph: Graph
    private val inverseGraph: Graph
    private val reachabilityGraph: Graph
    private val inverseReachabilityGraph: Graph

    init {
        println("${ZonedDateTime.now()}: balancing checksums started")
        values.forEachIndexed { index, av ->
            av.properties = IntArray(1)
            av.localIndex = index
        }
        color = ByteArray(values.size) { white }
        graph = buildGraph(values) { it.checksums }
        reachabilityGraph = buildReachabilityGraph(graph)
        inverseGraph = graph.inverse()
        inverseReachabilityGraph = reachabilityGraph.inverse()
    }

    fun process() {
        var optimized = 0

        fun Map<AVGE<C>, List<CGE<C>>>.sort() =
            entries.sortedWith(compareBy({ (av, cs) -> -(av.checksums.size - cs.size) }, { (_, cs) -> cs.size }))

        fun List<Map.Entry<AVGE<C>, List<CGE<C>>>>.tryToReverse(rhs: AVGE<C>, isComplete: (AVGE<C>) -> Boolean) =
            any { (lhs, checksumList) ->
                val dependsOn = checksumList.flatMapTo(FMutableSet()) { it.dependsOn }
                dependsOn -= lhs
                dependsOn -= rhs
                if (willGraphBeAcyclicAfterReverse(lhs, rhs, dependsOn)) {
                    checksumList.forEach { reverse(lhs, rhs, it) }
                    reverse(lhs, rhs, dependsOn)
                    optimized++
                }
                isComplete(rhs)
            }

        fun Iterable<AVGE<C>>.tryOptimize() = forEach { rhs ->
            val grouped = rhs.released.groupBy { cs -> cs.appVersions.single() }
            val freely = grouped
                .filter { (lhs, cs) ->
                    val lhsAfter = lhs.checksums.size - cs.size
                    val rhsAfter = rhs.checksums.size + cs.size
                    lhsAfter >= max && lhsAfter >= rhsAfter
                }
                .sort()
            val almostFreely = grouped
                .filter { (lhs, cs) ->
                    val lhsAfter = lhs.checksums.size - cs.size
                    val rhsAfter = rhs.checksums.size + cs.size
                    lhsAfter > max / 2 && lhsAfter < max && lhsAfter >= rhsAfter
                }
                .sort()

            val reversedFree = freely.tryToReverse(rhs) { it.checksums.size >= max }
            if (reversedFree) return@forEach
            val reversedAlmostFreely = almostFreely.tryToReverse(rhs) { it.checksums.size > max / 2 }
            if (reversedAlmostFreely) return@forEach
        }

        (1..1).any { iteration: Int ->
            val before = optimized
            (0..2).forEach { checksums ->
                println("${ZonedDateTime.now()}: started, |checksums| = $checksums; iteration $iteration")
                val before0 = optimized
                val optimizable =
                    values.filterTo(FMutableSet()) { it.checksums.size == checksums && it.released.size > 0 }
                println("${ZonedDateTime.now()}: optimizing, |checksums| = $checksums; iteration $iteration")
                optimizable.tryOptimize()
                println("${ZonedDateTime.now()}: balanced ${optimized - before0} / ${optimizable.size} app-versions with |checksums| = $checksums; iteration $iteration")
                println("${ZonedDateTime.now()}: coeff=${values.sumBy { it.checksums.size.coerceAtMost(max) }.toDouble() / avDict.size.toDouble()}")
//                verifyAvDictConsistency()
            }
            before == optimized
        }

        verifyAvDictConsistency()

        println("${ZonedDateTime.now()}: balancing checksums done")
    }

    private fun willGraphBeAcyclicAfterReverse(lhs: AVGE<C>, rhs: AVGE<C>, dependsOn: Collection<AVGE<C>>): Boolean {
        val lhsIndex = lhs.localIndex
        val rhsIndex = rhs.localIndex
        return inverseGraph[rhsIndex].none { it in reachabilityGraph[lhsIndex] && it != rhsIndex }
            && dependsOn.none { rhsIndex in reachabilityGraph[it.localIndex] }
    }

    //       cs
    // lower -> higher
    private fun reverse(lower: AVGE<C>, higher: AVGE<C>, cs: CGE<C>) {
        cs.dependsOn += lower
        cs.dependsOn -= higher
        cs.appVersions += higher
        cs.appVersions -= lower
        lower.released += cs
        lower.checksums -= cs
        higher.checksums += cs
        higher.released -= cs
    }

    /**
     * D <-- cs --> H       D <-- cs <-- H
     *       ^         =>         |
     *       |                    v
     *       L                    L
     *
     * cs - one of checksums that is 'transferred' from L to H.
     * D - list of vertices that are adjacent to L via cs and access to which is
     * L - [lower], i.e. that one which received cs from H during [PriorityQueueBasedSolution].
     * H - [higher], i.e. that one which released cs to L during [PriorityQueueBasedSolution].
     *
     * By given illustration, we see that after [ChecksumBalancer.reverse]:
     * - graph[D] is valid, as direct links from D are not affected by reverse
     * - graph[L] is invalid
     *   - H should be removed
     *   - removing D may be wrong, as if exists cs' owned by L and which depends on D, but not on H;
     *       graph[L] should be rebuilt from AVGE structure
     * - graph[H] is invalid
     *   - adding L and D should be enough
     *
     * - inverseGraph[L] is invalid
     *   - adding [H] should be enough
     * - inverseGraph[D] is invalid
     *   - H should be added
     *   - removing L may be wrong (see graph[L] expl.);
     *       if D is still in graph[L] after rebuilding, then inverseGraph[D] should keep L
     * - inverseGraph[H] is invalid
     *   - L should be removed
     *
     * - reachabilityMatrix[D] is valid and it's values are valid
     *   - if D could have reached L, then there was a cycle before reverse, i.e. graph was not valid
     *   - if D could have reached H, then there is a cycle after reverse, i.e. conversion was not valid and
     *       [ChecksumBalancer.willGraphBeAcyclicAfterReverse] is incorrect
     * - reachabilityMatrix[L] is invalid
     *   - should be rebuilt from graph[L]
     *     - if D was not removed from graph[L], then we can avoid adding/removing it's values
     * - reachabilityMatrix[H] is invalid
     *   - we have to add reachabilityMatrix[L] and reachabilityMatrix[D] to it
     *
     * - inverseReachabilityMatrix[L] is invalid
     *   - (old?) inverseReachabilityMatrix[H] has to be added to it
     * - inverseReachabilityMatrix[H] is invalid
     *
     * - inverseReachabilityMatrix[D] may be invalid
     *   - inverseReachabilityMatrix[H], H should be added
     *   - if L is in inverseGraph[D], then
     *
     */
    private fun reverse(lower: AVGE<C>, higher: AVGE<C>, dependsOn: Collection<AVGE<C>>) {
        val lowerIdx = lower.localIndex
        val higherIdx = higher.localIndex
        val doIndices = dependsOn.mapTo(IntArrayList(dependsOn.size)) { it.localIndex }

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


        fun dfs(from: Int, g: Graph, rg: Graph, shouldClear: Boolean = true) {
            if (color[from] == black) return

            color[from] = gray
            if (shouldClear) rg[from].clear()
            g[from].intForEach { to ->
                val color = color[to]
                when (color) {
                    gray ->
                        throw Exception("cyclic")
                    white -> dfs(to, g, rg)
                }
                if (!(color == black && !shouldClear)) {
                    rg[from].addAll(rg[to])
                    rg[from].add(to)
                }
            }
            color[from] = black
        }

        color.fill(white)
        dfs(higherIdx, inverseGraph, inverseReachabilityGraph)
        dfs(lowerIdx, inverseGraph, inverseReachabilityGraph)
        doIndices.forEach { dfs(it, inverseGraph, inverseReachabilityGraph) }
        reachabilityGraph[lowerIdx].forEach { dfs(it, inverseGraph, inverseReachabilityGraph) }
        reachabilityGraph[higherIdx].forEach { dfs(it, inverseGraph, inverseReachabilityGraph) }
        doIndices.forEach { doIdx ->
            reachabilityGraph[doIdx].forEach {
                dfs(
                    it,
                    inverseGraph,
                    inverseReachabilityGraph
                )
            }
        }

        color.fill(white)
        doIndices.forEach { color[it] = black }
        doIndices.forEach { doIdx -> reachabilityGraph[doIdx].forEach { color[it] = black } }
        reachabilityGraph[higherIdx].forEach { color[it] = black }

        dfs(lowerIdx, graph, reachabilityGraph)

        reachabilityGraph[higherIdx].addAll(doIndices)
        reachabilityGraph[higherIdx].add(lowerIdx)
        reachabilityGraph[higherIdx].addAll(reachabilityGraph[lowerIdx])

        doIndices.forEach { doIdx -> reachabilityGraph[higherIdx].addAll(reachabilityGraph[doIdx]) }
        inverseReachabilityGraph[lowerIdx].forEach { dfs(it, graph, reachabilityGraph) }
        inverseReachabilityGraph[higherIdx].forEach { dfs(it, graph, reachabilityGraph) }

//        doIndices.forEach { dfs(it, graph, reachabilityGraph) }
//        doIndices.forEach { doIdx -> inverseReachabilityGraph[doIdx].forEach { dfs(it, graph, reachabilityGraph) } }

//        verifyAvDictConsistency()
    }

    private fun verifyAvDictConsistency() {
        val pureGraph = buildGraph(values) { it.checksums }
        val errors = ArrayList<String>()

        if (graph != graph.inverse().inverse())
            errors += ("inverse() is invalid")
        if (graph != inverseGraph.inverse())
            errors += ("Graph and inverse graph are inconsistent")
        if (graph.inverse() != inverseGraph)
            errors += ("Graph and inverse graph are inconsistent")

        if (reachabilityGraph != reachabilityGraph.inverse().inverse())
            errors += ("inverse() is invalid")
        if (reachabilityGraph != inverseReachabilityGraph.inverse())
            errors += ("Reachability graph and inverse reachability graph are inconsistent")
        if (reachabilityGraph.inverse() != inverseReachabilityGraph)
            errors += ("Reachability graph and inverse reachability graph are inconsistent")

        if (graph != pureGraph)
            errors += ("Graphs are not equivalent?")
        if (inverseGraph != pureGraph.inverse())
            errors += ("Inverse graphs are not equivalent?")
        val pureReachabilityGraph = buildReachabilityGraph(pureGraph)
        if (reachabilityGraph != pureReachabilityGraph)
            errors += ("Reachability graphs are not equivalent?")
        if (inverseReachabilityGraph != pureReachabilityGraph.inverse())
            errors += ("Inverse reachability graphs are not equivalent?")

        fun dfs(from: Int) {
            color[from] = gray
            pureGraph[from].intForEach { to ->
                when (color[to]) {
                    gray -> errors.add("Graph is acyclic at from=$from to=$to")
                    white -> dfs(to)
                }
            }
            color[from] = black
        }

        color.fill(white)
        for (idx in pureGraph.indices) {
            if (color[idx] == white) {
                dfs(idx)
            }
        }

        if (errors.isNotEmpty()) {
            throw Exception(errors.toString())
        }
    }
}

typealias Graph = List<IntOpenHashSet>

const val iohsSize = 16
const val iohsLoad = 0.5f

inline fun <C : ChecksumKey<C>> buildGraph(list: Collection<AVGE<C>>, picker: (AVGE<C>) -> Collection<CGE<C>>) =
    list.map {
        picker(it).flatMapTo(IntOpenHashSet(iohsSize, iohsLoad)) { cs -> cs.dependsOn.map { av -> av.localIndex } }
    }

fun buildReachabilityGraph(graph: Graph): Graph {
    val white = 0.toByte()
    val gray = 1.toByte()
    val black = 2.toByte()

    val reachabilityGraph = MutableList(graph.size) { IntOpenHashSet(iohsSize, iohsLoad) }
    val color = ByteArray(graph.size) { white }

    fun dfs(from: Int) {
        color[from] = gray
        graph[from].intForEach { to ->
            when (color[to]) {
                gray -> {
                    throw Exception("Cycle in graph!")
                }
                white -> dfs(to)
            }
            reachabilityGraph[from].addAll(reachabilityGraph[to])
            reachabilityGraph[from].add(to)
        }
        color[from] = black
    }

    for (index in graph.indices) {
        if (color[index] == white) {
            dfs(index)
        }
    }
    return reachabilityGraph
}

fun Graph.inverse(): MutableList<IntOpenHashSet> {
    val inverseGraph = MutableList(size) { IntOpenHashSet(iohsSize, iohsLoad) }
    forEachIndexed { lhs, values ->
        values.intForEach { rhs ->
            inverseGraph[rhs].add(lhs)
        }
    }
    return inverseGraph
}
