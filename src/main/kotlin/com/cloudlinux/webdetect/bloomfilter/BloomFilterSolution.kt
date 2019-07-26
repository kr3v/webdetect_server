package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.DataContext
import java.io.PrintWriter

data class BloomFilterSolutionParameters(
    val bloomFilterFalsePositiveProbability: Double,
    val leafsPerNode: Int,
    val matchingThreshold: Double,
    val bloomFilterMinimumSize: Int
)

fun bloomFilterBasedSolution(
    solutionContext: BloomFilterSolutionParameters,
    dataContext: DataContext,
    toBeDetected: List<String>
) {
    val filter = buildHierarchicalBloomFilter(solutionContext, dataContext)
    val (matches, falsePositives) = doMatching(toBeDetected, filter)
    PrintWriter(System.out).print(solutionContext, matches, falsePositives)
}
