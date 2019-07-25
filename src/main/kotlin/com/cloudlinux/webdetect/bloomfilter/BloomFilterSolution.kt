package com.cloudlinux.webdetect.bloomfilter

import com.cloudlinux.webdetect.DataContext

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
    val filter = buildLayeredBloomFilter(solutionContext, dataContext)
    val (matches, falsePositives) = doMatching(toBeDetected, filter)
    debugPrint(solutionContext, matches, falsePositives)
}
