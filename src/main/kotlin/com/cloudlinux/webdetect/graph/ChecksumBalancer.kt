package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

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
        private const val white = 0
        private const val gray = 1
        private const val black = 2
    }

    private val color: IntArray
    private val graph: List<IntOpenHashSet>

    init {
        avDict.values.forEachIndexed { index, av ->
            av.properties = IntArray(1)
            av.localIndex = index
        }
        color = IntArray(avDict.values.size) { white }
        graph = avDict.values.map {
            it.checksums.flatMapTo(IntOpenHashSet(it.checksums.size + it.released.size, 0.5f)) { cs ->
                cs.dependsOn.map { av -> av.localIndex }
            }
        }
    }

    fun process() {
        var optimized = 0

        fun Map<AVGE<C>, List<CGE<C>>>.sort() =
            entries.sortedWith(compareBy({ (av, cs) -> -(av.checksums.size - cs.size) }, { (_, cs) -> cs.size }))

        fun List<Map.Entry<AVGE<C>, List<CGE<C>>>>.tryToReverse(rhs: AVGE<C>, isComplete: (AVGE<C>) -> Boolean) =
            any { (lhs, cs) ->
                cs.forEach { reverse(lhs, rhs, it) }
                if (!graphIsAcyclic(lhs, rhs)) {
                    cs.forEach { reverse(rhs, lhs, it) }
                } else {
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

        repeat(20) {
            val before = optimized
            avDict.values.filterTo(FMutableSet()) { it.checksums.size == 0 && it.released.size > 0 }.tryOptimize()
            println("Index $it: totally optimized $optimized after 0")
            avDict.values.filterTo(FMutableSet()) { it.checksums.size == 1 && it.released.size > 0 }.tryOptimize()
            println("Index $it: totally optimized $optimized after 1")
            avDict.values.filterTo(FMutableSet()) { it.checksums.size == 2 && it.released.size > 0 }.tryOptimize()
            println("Index $it: totally optimized $optimized after 2")
            if (before == optimized) return
        }
    }

    // takes all checksums released from rhs to lhs and reverses this: checksums are now released from lhs to rhs
    private fun reverse(lhs: AVGE<C>, rhs: AVGE<C>) =
        lhs.checksums
            .filter { rhs in it.dependsOn }
            .forEach { reverse(lhs, rhs, it) }

    //     cs
    // lhs -> rhs
    private fun reverse(lhs: AVGE<C>, rhs: AVGE<C>, cs: CGE<C>) {
        cs.dependsOn += lhs
        cs.dependsOn -= rhs
        cs.appVersions += rhs
        cs.appVersions -= lhs
        lhs.released += cs
        lhs.checksums -= cs
        rhs.checksums += cs
        rhs.released -= cs

        graph[lhs.localIndex] -= rhs.localIndex
        graph[rhs.localIndex] += lhs.localIndex
    }

    /**
     * @return true if [graph] has cycle through [e]
     */
    private fun dfs(e: AVGE<C>) = dfs(e.localIndex)

    /**
     * @return true if [graph] has cycle through [idx]
     */
    private fun dfs(idx: Int): Boolean {
        color[idx] = gray
        val intOpenHashSet = graph[idx]
        val iterator = intOpenHashSet.iterator()
        while (iterator.hasNext()) {
            val av = iterator.nextInt()
            when (color[av]) {
                white -> if (dfs(av)) return true
                gray -> return true
            }
        }
        color[idx] = black
        return false
    }

    /**
     * @idea
     * Idea is if [lhs] and [rhs] were reversed and graph was acyclic earlier, then new cycle, that may appear,
     * will contain both [lhs] and [rhs].
     * So, we can start dfs in [rhs] (as, after reverse, rhs depends on lhs, i.e. rhs -> lhs).
     *
     * @complexity
     * O(?), up to O(N^2), where N - count of all (app, version)
     *
     * @impl http://e-maxx.ru/algo/finding_cycle
     */
    private fun graphIsAcyclic(lhs: AVGE<C>, rhs: AVGE<C>): Boolean {
        color.fill(white)
        return !dfs(rhs)
    }

    @Deprecated("does not work", level = DeprecationLevel.HIDDEN)
    private fun old() {
        csDict.entries
            .sortedBy { (k, _) -> k }
            .forEach { (_, cs) ->
                val previousOwner = cs.appVersions.single()
                val receiver = (cs.dependsOn + previousOwner)
                    .minWith(compareBy({ it.checksums.size }, { it.released.size }))
                    ?.takeIf { it != previousOwner }
                    ?: return@forEach

                reverse(previousOwner, receiver, cs)
            }
    }
}
