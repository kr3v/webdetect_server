package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.ChecksumLong
import com.cloudlinux.webdetect.MutableSet
import com.cloudlinux.webdetect.util.toList
import it.unimi.dsi.fastutil.objects.ObjectArrayList

class HierarchicalBloomFilterBuilder(
    private val solutionContext: BloomFilterSolutionParameters
) {
    fun <V> build(
        filters: List<Pair<ImmutableBloomFilter, V>>
    ): HierarchicalBloomFilter<V> {
        val root = mergeNodes(filters.map { (k, v) -> HierarchicalBloomFilter.Node.Leaf(k, v) })
        return HierarchicalBloomFilter(root)
    }

    private tailrec fun <V> mergeNodes(list: List<HierarchicalBloomFilter.Node<V>>): HierarchicalBloomFilter.Node<V> =
        when (list.size) {
            1 -> list.single()
            else -> {
                val chunked = list.chunked(solutionContext.leafsPerNode)
                mergeNodes(chunked.parallelStream().unordered().map { mergeNodeChunk(it) }.toList(chunked.size))
            }
        }

    private fun <V> mergeNodeChunk(list: List<HierarchicalBloomFilter.Node<V>>) = when (list.size) {
        1 -> HierarchicalBloomFilter.Node.Intermediate(list.single().filter, list)
        else -> {
            val checksums = MutableSet<Checksum>(list.sumBy { it.filter.items.size }, 1f)
            list.forEach { checksums.addAll(it.filter.items) }
            HierarchicalBloomFilter.Node.Intermediate(
                bloomFilter(checksums, solutionContext),
                list.toList()
            )
        }
    }
}

class HierarchicalBloomFilter<V>(
    private val root: Node<V>
) {
    fun lookup(csl: ChecksumLong): List<Node.Leaf<V>> {
        val result = ObjectArrayList<Node.Leaf<V>>()
        root.lookup(csl, result)
        return result
    }

    fun size() = root.size()

    sealed class Node<V>(
        val filter: ImmutableBloomFilter
    ) {
        abstract fun lookup(csl: ChecksumLong, result: MutableList<Leaf<V>>)
        abstract fun size(): Long

        class Leaf<V>(filter: ImmutableBloomFilter, val value: V) : Node<V>(filter) {
            override fun lookup(csl: ChecksumLong, result: MutableList<Leaf<V>>) {
                if (filter.contains(csl))
                    result.add(this)
            }

            override fun size(): Long = filter.config.size().toLong()
        }

        class Intermediate<V>(filter: ImmutableBloomFilter, private val leafs: List<Node<V>>) : Node<V>(filter) {
            override fun lookup(csl: ChecksumLong, result: MutableList<Leaf<V>>) {
                if (filter.contains(csl))
                    leafs.forEach { it.lookup(csl, result) }
            }

            override fun size(): Long = filter.config.size() + leafs.fold(0L) { acc, node -> acc + node.size() }
        }
    }
}