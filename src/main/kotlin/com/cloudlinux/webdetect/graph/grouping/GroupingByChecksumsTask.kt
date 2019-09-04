package com.cloudlinux.webdetect.graph.grouping

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.FMutableMap
import com.cloudlinux.webdetect.FMutableSet
import com.cloudlinux.webdetect.FSortedSet
import com.cloudlinux.webdetect.WebdetectContext
import com.cloudlinux.webdetect.graph.AppVersionGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumGraphEntry
import com.cloudlinux.webdetect.graph.ChecksumKey
import com.cloudlinux.webdetect.graph.createGraph
import com.cloudlinux.webdetect.util.countIntersects
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.util.stream.Collectors

typealias Group<C> = Map.Entry<Set<String>, List<ChecksumGraphEntry<C>>>

data class VersionToChecksumGroup<C : ChecksumKey<C>>(
    val versions: Set<String>,
    val checksums: List<ChecksumGraphEntry<C>>,
    var canBeSolved: Int,
    private val hashCode: Int = versions.hashCode()
) : Group<C> {

    override val key: Set<String>
        get() = versions
    override val value: List<ChecksumGraphEntry<C>>
        get() = checksums

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VersionToChecksumGroup<*>

        if (versions != other.versions) return false

        return true
    }

    override fun hashCode() = hashCode
}

/**
 * TODO list:
 *  1. Avoid deducing list of checksums used by BFS task here, it's not this task responsibility
 *  2. Avoid duplicating [VersionToChecksumGroup] in [solveWithinApp] and [secondStage]
 *  3. This whole thing need to be rewritten to support [AppVersion.Single.group]
 */
class GroupingByChecksumsTask<C : ChecksumKey<C>>(
    private val webdetectCtx: WebdetectContext<C>,
    private val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>,
    private val definedAvDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>
) {
    fun findGroups(params: Params) {
        val allApps = avDict.keys
            .flatMap { it.appVersions() }
            .map { it.app to it.version }
            .groupByTo(
                FMutableMap(),
                { (k, _) -> k },
                { (_, v) -> v }
            )
        avDict.clear()
        avDict.trim()

        // we need 'real' graph before it was modified by BFS task
        val (_, csDict) = createGraph(webdetectCtx.checksumToAppVersions, webdetectCtx.appVersionsToChecksums)

        val usedInGraphTask = definedAvDict
            .values
            .stream()
            .flatMap { it.checksums.stream().sorted(compareBy { it.dependsOn.size }).limit(params.bfsMax.toLong()) }
            .map { it.key }
            .collect(Collectors.toSet())

        csDict.keys.removeAll(usedInGraphTask)
        usedInGraphTask.clear()

        val result = FMutableMap<String, FMutableMap<FMutableSet<String>, ObjectArrayList<ChecksumGraphEntry<C>>>>()
        for (cs in csDict.values) {
            val apps = cs.appVersions.flatMapTo(FMutableSet()) { it.key.apps() }
            if (apps.size != 1) continue
            val app = apps.single()
            val versions = cs.appVersions.flatMapTo(FMutableSet()) { it.key.versions() }
            result
                .computeIfAbsent(app) { FMutableMap() }
                .computeIfAbsent(versions) { ObjectArrayList() }
                .add(cs)
        }
        csDict.clear()
        csDict.trim()

        for ((app, values) in result) {
            val allVersions = allApps.getValue(app).toCollection(FMutableSet())
            val reallyAvailableVersions = values.flatMapTo(FMutableSet()) { it.key }
            val (groups, unsolved) = solveWithinApp(
                app,
                values,
                reallyAvailableVersions,
                params
            )
            println("Grouping for $app (${(unsolved + (allVersions - reallyAvailableVersions)).size} were unsolved):")
            groups.forEach { println("\t${it.versions.size} -> ${it.checksums.size}") }
            println()
        }
    }

    /// todo: [bfsMax] should not be passed directly, we should not modify definedAvDict here.
    data class Params(
        val minimumPerEntry: Int,
        val bfsMax: Int
    )


    private fun solveWithinApp(
        app: String,
        versionsDict: FMutableMap<FMutableSet<String>, ObjectArrayList<ChecksumGraphEntry<C>>>,
        needToBeSolved: FMutableSet<String>,
        params: GroupingByChecksumsTask.Params
    ): Pair<FMutableSet<VersionToChecksumGroup<C>>, List<AppVersion.Single>> {
        val result = FMutableSet<VersionToChecksumGroup<C>>()

        versionsDict.entries.removeIf { (_, it) -> it.size < params.minimumPerEntry }
        versionsDict.entries.removeIf { (k, _) -> k.size == 1 }
        val canBeSolved = versionsDict.keys.flatMapTo(FMutableSet()) { it }
        val unsolved = (needToBeSolved - canBeSolved).map { AppVersion.Single(app, it) }
        val versionToGroups = FMutableMap<String, FMutableSet<VersionToChecksumGroup<C>>>()
        for (entry in versionsDict.entries) {
            val (k, _) = entry
            for (version in k) {
                versionToGroups.computeIfAbsent(version) { FMutableSet() }
                    .add(VersionToChecksumGroup(entry.key, entry.value, countIntersects(entry.key, canBeSolved)))
            }
        }

        firstStage(canBeSolved, versionsDict, result, versionToGroups)
        if (canBeSolved.isNotEmpty()) secondStage(canBeSolved, versionsDict, result, versionToGroups)
        return result to unsolved
    }

    private fun firstStage(
        canBeSolved: FMutableSet<String>,
        groupDict: FMutableMap<out Set<String>, out List<ChecksumGraphEntry<C>>>,
        result: FMutableSet<VersionToChecksumGroup<C>>,
        versionToGroups: FMutableMap<String, FMutableSet<VersionToChecksumGroup<C>>>
    ) {
        val groupsWithSingleGroupedVersions = versionToGroups
            .values.filter { it.size == 1 }.flatMapTo(FMutableSet()) { it }
        result.addAll(groupsWithSingleGroupedVersions)
        groupDict.entries.removeAll(groupsWithSingleGroupedVersions)
        val solvedVersions = groupsWithSingleGroupedVersions.flatMapTo(FMutableSet()) { it.key }
        canBeSolved.removeAll(solvedVersions)
        versionToGroups.keys.removeAll(solvedVersions)
    }

    private fun secondStage(
        canBeSolved: FMutableSet<String>,
        groupDict: FMutableMap<FMutableSet<String>, ObjectArrayList<ChecksumGraphEntry<C>>>,
        result: FMutableSet<VersionToChecksumGroup<C>>,
        versionToGroups: FMutableMap<String, FMutableSet<VersionToChecksumGroup<C>>>
    ): FMutableSet<VersionToChecksumGroup<C>> {
        val c = compareBy<VersionToChecksumGroup<C>>({ it.canBeSolved }, { it.versions.size }, { it.checksums.size })
            .thenComparing { o1, o2 ->
                if (o1 == o2) 0
                else {
                    val h1 = System.identityHashCode(o1)
                    val h2 = System.identityHashCode(o2)
                    if (h1 == h2) o1.toString().compareTo(o2.toString())
                    else h1 - h2
                }
            }
        val v = FSortedSet<VersionToChecksumGroup<C>>(c)
        groupDict.entries.mapTo(v) { VersionToChecksumGroup(it.key, it.value, countIntersects(it.key, canBeSolved)) }

        while (canBeSolved.isNotEmpty() && v.isNotEmpty()) {
            val e = v.last()
            if (e in result) {
                println(":(")
            }
            result.add(e)

            val versions = e.versions intersect canBeSolved
            canBeSolved -= versions

            val affectedGroups = versions.flatMapTo(FMutableSet()) { versionToGroups[it].orEmpty() }
            v.removeAll(affectedGroups)
            affectedGroups.remove(e)
            affectedGroups.forEach { it.canBeSolved = countIntersects(it.versions, canBeSolved) }
            e.versions.forEach { versionToGroups[it]?.remove(e) }
            v.addAll(affectedGroups)
        }
        return result
    }
}