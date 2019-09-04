package com.cloudlinux.webdetect.graph

import com.cloudlinux.webdetect.AppVersion
import com.cloudlinux.webdetect.Checksum
import com.cloudlinux.webdetect.FMutableMap
import java.util.Comparator

var HasIntProperties.releasableChecksums
    get() = properties[0]
    set(value) {
        properties[0] = value
    }

var HasIntProperties.pqIndex
    get() = properties[1]
    set(value) {
        properties[1] = value
    }

var HasIntProperties.localIndex
    get() = properties[2]
    set(value) {
        properties[2] = value
    }

class ChecksumBalancer<C : ChecksumKey<C>>(
    val avDict: FMutableMap<AppVersion, AppVersionGraphEntry<C>>,
    val csDict: FMutableMap<Checksum, ChecksumGraphEntry<C>>,
    val maxChecksums: Int
) {
    private val cmp = Comparator.comparingInt<AppVersionGraphEntry<C>> { it.checksums.size - maxChecksums }

    fun process() {
//        avDict.values.forEachIndexed { index, av -> av.properties = IntArray(2) }
//            av.localIndex = index
//        }
//        val sortedCs = avDict.values.mapTo(ArrayList(avDict.size)) { v -> v.checksums.sortedBy { it.dependsOn.size } }
//        avDict.values.forEach { av ->
//            av.releasableChecksums =
//        }
//        val pq = PriorityQueue<AppVersionGraphEntry>(avDict.values, cmp)
    }

    private fun old() {
        csDict.entries
            .sortedBy { (k, _) -> k }
            .forEach { (_, cs) ->
                val previousOwner = cs.appVersions.single()
                val receiver = (cs.dependsOn + previousOwner)
                    .minWith(compareBy({ it.checksums.size }, { it.released.size }))
                    ?.takeIf { it != previousOwner }
                    ?: return@forEach

                cs.dependsOn += previousOwner
                cs.dependsOn -= receiver
                cs.appVersions += receiver
                cs.appVersions -= previousOwner
                previousOwner.released += cs
                previousOwner.checksums -= cs
                receiver.checksums += cs
                receiver.released -= cs
            }
    }

}