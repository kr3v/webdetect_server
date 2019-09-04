package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.WebdetectContext
import com.cloudlinux.webdetect.graph.bfs.BfsBasedSolution
import com.cloudlinux.webdetect.graph.grouping.FindAppVersionsInclusionsTask
import com.cloudlinux.webdetect.graph.grouping.MergeAppVersionsWithSameChecksumsTask
import com.cloudlinux.webdetect.graph.pq.PriorityQueue
import com.cloudlinux.webdetect.graph.pq.PriorityQueueBasedSolution
import java.time.ZonedDateTime

data class GraphBasedSolutionResult<C : ChecksumKey<C>>(
    val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>,
    val csDict: FMutableMap<C, ChecksumGraphEntry<C>>,
    val definedAvDict: Map<AppVersion, AppVersionGraphEntry<C>>,
    val undefinedAvDict: Map<AppVersion, AppVersionGraphEntry<C>>
)

/**
 * @terminology
 * - file/checksum - used interchangeably;
 * - app-version - tuple of application and version;
 * - [...] represent an array and regex-like 'or' shorthand if used after symbols (i.e. ch[1-3] == [ch1, ch2, ch3]).
 *
 * @idea
 * Solution idea is following: let's consider we have N versions of the same application. Let's enumerate them from 1 to N.
 *
 * Normally, versions are released sequentially, so the version with index N has some files that are unique to it.
 * We can use these files to identify this version on client-side: if those files are present there, then only version
 * with index N matches, as none other version has those files.
 * So, we have version with index N 'identified' by those files (that, again, are present only in version with index N).
 * Version N (and appropriate app-version) is said 'defined', if it has files that identify it in described terms.
 *
 * All other files (that were not used by identifying version N) are 'released' (i.e. we remove a reference to version N from them).
 * If checksum C is released from version I (and not used to define version I), then we say that checksum C 'depends on' version I.
 * Depends-on relation allows to identify versions which were defined by released checksums (see sample)
 *
 * @implementation
 * Basically, we can't always say which app's version is the last one (also, it does not help, if checksums are shared between apps).
 * So, we should just find all app-versions which have enough checksums to be defined (i.e. some root set).
 * By releasing not used in identifying checksums from them, we make more app-versions to be defined.
 * On the other side, it looks like BFS algorithm (@see [BfsBasedSolution]):
 * - put in the BFS queue all root set entries;
 * - until queue is not empty, pop an entry from it's head - mark popped entry as defined and release 'unused' checksums;
 * - add to end of queue all app-versions that are adjacent to popped entry through checksums).
 *
 * [PriorityQueueBasedSolution] uses [PriorityQueue] instead of BFS queue with specific comparator to improve accuracy.
 *
 * @sample
 * Let's consider following example:
 * - version 1: [cs1, cs2, cs3],
 * - version 2: [cs1, cs2, cs3, cs4, cs5, cs6].
 * According to described algorithm:
 * - version 1 is identified via [cs1, cs2, cs3],
 * - version 2 is identified via [cs4, cs5, cs6].
 * - cs[1-3] depend on version 2
 *
 * Following cases are possible
 * - client has version 2 (i.e. all cs[1-6] files)
 * By cs[1-3] we identified version 1 and cs[4-6] identified version 2.
 * But, cs[1-3] depend on version 2 (as they were released from it) and version 2 is present.
 * So we discard version 1 and only version 2 is shown as a result.
 *
 * - client has version 1 (all cs[1-3] files)
 * We only identify version 1 by cs[1-3]. Version 2 was not identified, so version 1 is not discarded and shown as a result.
 *
 * @client
 * todo
 */
fun <C : ChecksumKey<C>> graphBasedSolution(webdetectCtx: WebdetectContext<C>): GraphBasedSolutionResult<C> {
    println("${ZonedDateTime.now()}: creating graph started")
    val (avDict, csDict) = createGraph(
        // filtering significantly speeds-up PQ performance by excluding checksums that are shared between big amount of app-versions (empty file, for example)
        // Fibonacci heap may do the same without filtering by removing log N at key updating
        // on other hand, we must exclude checksums representing, for example, empty file to avoid false-positives, so just optimization might not help
        webdetectCtx.checksumToAppVersions.filterValues { it.flatMapTo(FMutableSet(), AppVersion::apps).size <= 50 },
        webdetectCtx.appVersionsToChecksums
    )
    println("${ZonedDateTime.now()}: creating graph done")
    webdetectCtx.cleanup()

    MergeAppVersionsWithSameChecksumsTask(avDict).process()
    FindAppVersionsInclusionsTask(avDict).process()

    PriorityQueueBasedSolution(avDict).process()
    val definedAvDict = avDict.filterValues { it.checksums.size > 0 }

    // val maxChecksums = 5
    // statsGraph(avDict.filterValues { it.checksums.size > 0 }, avDict, maxChecksums, 0)
    // ChecksumBalancer(avDict, csDict, maxChecksums).process()
    // statsGraph(avDict.filterValues { it.checksums.size > 0 }, avDict, maxChecksums, 1)

    val undetected = avDict - definedAvDict.keys
    return GraphBasedSolutionResult<C>(
        avDict,
        csDict,
        definedAvDict,
        undetected
    )
}
