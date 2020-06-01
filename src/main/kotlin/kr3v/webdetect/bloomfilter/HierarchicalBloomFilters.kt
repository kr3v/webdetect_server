package kr3v.webdetect.bloomfilter

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kr3v.webdetect.ChecksumLong
import kr3v.webdetect.FMutableSet
import kr3v.webdetect.util.toList

class HierarchicalBloomFilterBuilder(private val solutionContext: BloomFilterSolutionParameters) {
    fun <V> build(filters: List<Pair<ImmutableBloomFilter, V>>): HierarchicalBloomFilter<V> {
        val root = mergeNodes(filters.map { (k, v) -> Node.Leaf(k, v) })
        return HierarchicalBloomFilter(root)
    }

    private tailrec fun <V> mergeNodes(list: List<Node<V>>): Node<V> =
        when (list.size) {
            1 -> list.single()
            else -> {
                val chunked = list.chunked(solutionContext.leafsPerNode)
                mergeNodes(chunked.parallelStream().unordered().map { mergeNodeChunk(it) }.toList(chunked.size))
            }
        }

    private fun <V> mergeNodeChunk(list: List<Node<V>>) = when (list.size) {
        1 -> Node.Intermediate(list.single().filter, list)
        else -> {
            val checksums = list.flatMapTo(FMutableSet(list.sumBy { it.filter.items.size })) { it.filter.items }
            Node.Intermediate(bloomFilter(checksums, solutionContext), list.toList())
        }
    }
}

class HierarchicalBloomFilter<V>(private val root: Node<V>) {
    fun lookup(csl: ChecksumLong): List<Node.Leaf<V>> {
        val result = ObjectArrayList<Node.Leaf<V>>()
        root.lookup(csl, result)
        return result
    }

    fun size() = root.size
}

sealed class Node<V>(val filter: ImmutableBloomFilter) {
    abstract fun lookup(c: ChecksumLong, result: MutableList<Leaf<V>>)
    abstract val size: Long

    class Leaf<V>(filter: ImmutableBloomFilter, val value: V) : Node<V>(filter) {
        override fun lookup(c: ChecksumLong, result: MutableList<Leaf<V>>) {
            if (c in filter)
                result += this
        }

        override val size: Long = filter.config.size().toLong()
    }

    class Intermediate<V>(filter: ImmutableBloomFilter, private val leafs: List<Node<V>>) : Node<V>(filter) {
        override fun lookup(c: ChecksumLong, result: MutableList<Leaf<V>>) {
            if (c in filter)
                leafs.forEach { it.lookup(c, result) }
        }

        override val size: Long = filter.config.size() + leafs.fold(0L) { acc, node -> acc + node.size }
    }
}
