package kr3v.webdetect.graph

import kr3v.webdetect.AppVersion
import kr3v.webdetect.Checksum
import kr3v.webdetect.FMutableMap
import kr3v.webdetect.FMutableSet
import kr3v.webdetect.WebdetectContext
import kr3v.webdetect.buildContextByCsv
import kr3v.webdetect.buildDepths
import kr3v.webdetect.graph.bfs.BfsBasedSolution
import kr3v.webdetect.graph.grouping.FindAppVersionsInclusionsTask
import kr3v.webdetect.graph.grouping.MergeAppVersionsWithSameChecksumsTask
import kr3v.webdetect.graph.pq.PriorityQueue
import kr3v.webdetect.graph.pq.PriorityQueueBasedSolution
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.Optional

data class GraphBasedSolutionResult(
    val avDict: FMutableMap<AppVersion, AppVersionGraphEntry>,
    val csDict: FMutableMap<Checksum, ChecksumGraphEntry>,
    val definedAvDict: Map<AppVersion, AppVersionGraphEntry>,
    val undefinedAvDict: Map<AppVersion, AppVersionGraphEntry>
)


private const val undetectedOutputPath = "undetected"
private const val matricesOutputPath = "matrices"

/**
 * @intro
 * [in] is path to list of known apps, versions, checksums, depths with format:
 * <app name>\t<app version>\t<sha256 checksum as hex string>\t<depth in app-version files hierarchy>\n
 *
 * [WebdetectContext] keeps dump parsed by [buildContextByCsv]. Mainly, it is used to deduplicate strings/checksums in heap.
 *
 * [createGraph] is used to create graph-like objects [ChecksumGraphEntry] and [AppVersionGraphEntry] to simplify further processing.
 * Graph is represented via:
 * - [ChecksumGraphEntry.appVersions] contains list of app-versions where this checksum is present.
 * - [AppVersionGraphEntry.checksums] contains list of checksums which are present in this app-version.
 *
 * [PriorityQueueBasedSolution] is main core of solution, it chooses which checksums will 'define' which app-versions (see @idea section)
 * Different optimizations are run before or after it (see @optimizations section).
 *
 * Set of depths should be kept per each tuple (app-version, checksum), we should not keep it per checksum
 * as mixing different app-version will break path deducing on client side.
 * On other hand, keeping all depths correctly implies creating a lot of different objects and 4x of CSV size was not enough to keep them all in memory.
 * So, depths are parsed later in [buildDepths] / [SerializableChecksumsChooser] to be used in [GraphBasedSolutionSerializer].
 *
 * [GraphBasedSolutionSerializer] used to serialize everything to appropriate formats (see @serialization section).
 *
 * Information about client-side of detecting app-versions is in @client section.
 *
 * @terminology
 * - file/checksum - used interchangeably;
 * - app-version - tuple of application and version;
 * - len(...) - length of argument (len([1, 2, 3]) == 3)
 * - [...] can represent:
 *   - an array of items ([ch1, ch2, ch3])
 *   - regex-like 'or' shorthand if used after symbols (i.e. ch[1-3] == [ch1, ch2, ch3]).
 *   - integer part of math expression ([len([1, 2, 3]) / 2] == 1)
 *   - reference to method/class/field in terms of KDoc
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
 * Basically, we can't always say which app's version is the last one (also, it does not help, if checksums are shared between apps).
 * So, we should just find all app-versions which have enough checksums to be defined (i.e. some root set).
 * By releasing not used in identifying checksums from them, we make more app-versions to be defined.
 *
 * On the other side, it looks like BFS algorithm (@see [BfsBasedSolution]):
 * 0) put in the BFS queue all root set entries;
 * 1) pop an entry from it's head - mark popped entry as defined and release 'unused' checksums;
 * 2) add to end of queue all app-versions that are adjacent to popped entry through checksums and became defined during release;
 * 3) goto 1 if queue is not empty, otherwise we are 'done'
 *
 * [PriorityQueueBasedSolution] uses [PriorityQueue] instead of BFS queue with specific comparator to improve accuracy.
 *
 * Difference between [PriorityQueueBasedSolution] and [BfsBasedSolution] is that if there are no app-versions with > 0
 * checksums which are linked to only single app-versions (i.e. there are no app-versions that are defined by any checksum),
 * then [PriorityQueueBasedSolution] considers one app-version as non-detectable and releases all it's checksums.
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
 * @optimizations
 * @see MergeAppVersionsWithSameChecksumsTask
 * @see AppVersion.Merged
 * If two or more app-versions have the same list of checksums, then they are merged into single app-version.
 * Executed before [BfsBasedSolution]/[PriorityQueueBasedSolution].
 *
 * @see FindAppVersionsInclusionsTask
 * @see AppVersionGraphEntry.implies
 * Let's consider app-versions A and B such that checksums of A are subset of checksums of B, i.e. all checksums of A are included into B.
 * Due to depends-on semantics, we will exclude app-version A whenever app-version B is present (as all checksums of A 'depends-on' B).
 * This 'implies' relation to report app-version A as detected on client iff:
 * - it is excluded by depends-on relation
 * - all other conditions of 'detected' app-version match app-version A
 *
 * @see ChecksumBalancer
 * todo
 *
 * @client
 * App-version A is reported as detected during lookup if:
 * - at least ([len([AppVersionGraphEntry.checksums]) / 2] + 1) are found during lookup (aka 0.5-filter)
 * - at least one checksum does not 'depends-on' on any detected app-version
 * - it is 'implied' by any other detected app-version and:
 *   - it is not valid by 'depends-on'
 *   - it is valid by 0.5-filter
 *
 * Client implementation can be found in webdetect_client:client/webdetect.py.
 *
 * todo:
 *   1. Path detecting
 *   2. How webdetect client implementation depends on path deducing
 *
 * @serialization
 * @see GraphBasedSolutionSerializer
 * todo
 *
 * @todo list
 *   1. Unfinished sections here
 *   2. [GraphBasedSolutionSerializer]
 *     - create diffs between DBs? preserve DB version in it?
 */
fun graphSolution(`in`: String, out: String, maxChecksums: Int): GraphBasedSolutionResult {
    val ctx = buildContextByCsv(`in`)
    val (avDict, csDict) = createGraph(
        // filtering significantly speeds-up PQ performance by excluding checksums that are shared between big amount of app-versions (empty file, for example)
        // Fibonacci heap may do the same without filtering by removing log N at key updating
        // on other hand, we must exclude checksums representing, for example, empty file to avoid false-positives, so just optimization might not help
        // BfsBasedSolution seemed to ignored this case (but its resulting stats are still worse than PQ, so ...)
        ctx.csToAv.filterValues { it.flatMapTo(FMutableSet(), AppVersion::apps).size <= 10 },
        ctx.avToCs
    )
    ctx.cleanup()

    MergeAppVersionsWithSameChecksumsTask(avDict).process()
    FindAppVersionsInclusionsTask(avDict).process()
    PriorityQueueBasedSolution(avDict).process()
    val definedAvDict = avDict.filterValues { it.checksums.all { cs -> cs.appVersions.size == 1 } }
    ChecksumBalancer(definedAvDict).process()

    val undetected = avDict - definedAvDict.keys

    statsGraph(definedAvDict, avDict, maxChecksums)

    val serializableChecksumsChooser = SerializableChecksumsChooser(ctx.pathToCsv, csDict, definedAvDict, maxChecksums)
    GraphBasedSolutionSerializer(avDict, definedAvDict, serializableChecksumsChooser).serialize(
        Optional.of("$out.json"),
        Optional.of("$out.ldb")
    )

    writeUndetected(undetected.keys, avDict, PrintWriter(FileOutputStream(File(undetectedOutputPath))))

    return GraphBasedSolutionResult(avDict, csDict, definedAvDict, undetected)
}
