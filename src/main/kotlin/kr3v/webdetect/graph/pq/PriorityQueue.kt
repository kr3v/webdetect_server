package kr3v.webdetect.graph.pq

import kr3v.webdetect.graph.HasIntProperties
import java.util.Comparator

/**
 * Max-PQ implementation via binary heap on array. Uses [pqIndex] as index within queue to support [update].
 */
internal class PriorityQueue<T : HasIntProperties> private constructor(
    private val values: MutableList<T>,
    private val cmp: Comparator<T>,
    private var last: Int
) {

    constructor(values: Collection<T>, cmp: Comparator<T>) : this(
        values.mapIndexedTo(ArrayList(values.size)) { idx, it ->
            it.pqIndex = idx
            it
        },
        cmp,
        values.size - 1
    )

    init {
        for (it in values.indices) {
            up(it)
        }
    }

    private operator fun T.compareTo(rhs: T) = cmp.compare(this, rhs)

    fun pop(): T {
        swap(0, last--)
        down(0)
        return values[last + 1]
    }

    fun update(t: T) {
        up(t.pqIndex)
//        down(t.pqIndex)
    }

    fun isEmpty() = last == -1
    fun isNotEmpty() = last >= 0

    // approx.
    val size: Int get() = last

    private fun up(idx: Int) {
        var i = idx
        while (i != 0) {
            val parent = (i - 1) / 2
            if (values[i] <= values[parent])
                break
            i = swap(i, parent)
        }
    }

    private fun down(idx: Int) {
        var i = idx
        while (i * 2 + 1 <= last) {
            val idxValue = values[i]
            val child1 = i * 2 + 1
            val child2 = i * 2 + 2

            if (child2 > last) {
                if (values[child1] > idxValue)
                    swap(i, child1)
                break
            }

            i = when {
                idxValue < values[child1] && values[child1] >= values[child2] -> swap(i, child1)
                idxValue < values[child2] && values[child2] >= values[child1] -> swap(i, child2)
                else -> return
            }
        }
    }

    private fun swap(idx1: Int, idx2: Int): Int {
        values[idx1].pqIndex = idx2
        values[idx2].pqIndex = idx1

        val temp = values[idx1]
        values[idx1] = values[idx2]
        values[idx2] = temp
        return idx2
    }
}
