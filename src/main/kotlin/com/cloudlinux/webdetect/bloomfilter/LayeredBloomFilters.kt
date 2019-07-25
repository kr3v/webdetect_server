package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.MutableSet

class LayeredBloomFilterBuilder(
    private val solutionContext: BloomFilterSolutionParameters
) {
    fun <V> build(
        filters: List<Pair<ImmutableBloomFilter, V>>
    ): LayeredBloomFilter<V> {
        val root = mergeNodes(filters.map { (k, v) -> LayeredBloomFilter.Node.Leaf(k, v) })
        return LayeredBloomFilter(root)
    }

    private tailrec fun <V> mergeNodes(list: List<LayeredBloomFilter.Node<V>>): LayeredBloomFilter.Node<V> =
        when (list.size) {
            1 -> list.single()
            else -> mergeNodes(list.chunked(solutionContext.leafsPerNode, ::mergeNodeChunk))
        }

    private fun <V> mergeNodeChunk(list: List<LayeredBloomFilter.Node<V>>) = when (list.size) {
        1 -> LayeredBloomFilter.Node.Intermediate(list.single().filter, list)
        else -> {
            val checksums = MutableSet<Checksum>(list.sumBy { it.filter.items.size }, 1f)
            list.forEach { checksums.addAll(it.filter.items) }
            LayeredBloomFilter.Node.Intermediate(
                bloomFilter(checksums, solutionContext),
                list.toList()
            )
        }
    }
}

class LayeredBloomFilter<V>(
    private val root: Node<V>
) {
    fun lookup(l1: Long, l2: Long) = root.lookup(l1, l2)

    sealed class Node<V>(
        val filter: ImmutableBloomFilter
    ) {
        abstract fun lookup(l1: Long, l2: Long): List<Leaf<V>>

        class Leaf<V>(filter: ImmutableBloomFilter, val value: V) : Node<V>(filter) {
            override fun lookup(l1: Long, l2: Long) =
                if (filter.contains(l1, l2)) listOf(this)
                else emptyList()
        }

        class Intermediate<V>(filter: ImmutableBloomFilter, private val leafs: List<Node<V>>) :
            Node<V>(filter) {
            override fun lookup(l1: Long, l2: Long) =
                if (filter.contains(l1, l2)) leafs.map { it.lookup(l1, l2) }.flatten()
                else emptyList()
        }
    }
}