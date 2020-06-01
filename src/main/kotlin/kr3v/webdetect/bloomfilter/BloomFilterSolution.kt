package kr3v.webdetect.bloomfilter

import kr3v.webdetect.WebdetectContext
import java.io.File
import java.io.PrintWriter
import java.util.Optional

data class BloomFilterSolutionParameters(
    val bfFpProbability: Double,
    val leafsPerNode: Int,
    val matchingThreshold: Double,
    val bfMinimumSize: Int
)

fun bloomFilterBasedSolution(
    solutionContext: BloomFilterSolutionParameters,
    dataContext: WebdetectContext,
    toBeDetected: List<String>
) {
    val filter = buildHierarchicalBloomFilter(solutionContext, dataContext)
    val (matches, falsePositives) = doMatching(toBeDetected, filter)
    PrintWriter(System.out).print(solutionContext, matches, falsePositives)
}

fun bloomFilterSolution(
    webdetectCtx: WebdetectContext,
    detect: Optional<String>
) {
    bloomFilterBasedSolution(
        BloomFilterSolutionParameters(
            bfFpProbability = 0.01,
            leafsPerNode = 2,
            matchingThreshold = 0.5,
            bfMinimumSize = 100
        ),
        webdetectCtx,
        File(detect.get()).readLines()
    )
}
