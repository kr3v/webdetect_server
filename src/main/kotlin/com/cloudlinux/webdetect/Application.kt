package com.cloudlinux.webdetect

fun main() {
    val db = "../sha.list.new"
    val (checksumToAppVersions, appVersions) = read(db)
    Pool.appVersions.clear()
    Pool.appVersions.trim()
    Pool.checksums.clear()
    Pool.checksums.trim()
    Pool.strings.clear()
    Pool.strings.trim()

    val avDict = createGraph(checksumToAppVersions, appVersions)
    checksumToAppVersions.clear()
    checksumToAppVersions.trim()
    appVersions.clear()
    appVersions.trim()

    println(5)
    val result = findDefinedAppVersions(avDict, 5)
    (4 downTo 1).forEach {
        println(it)
        result += findDefinedAppVersions(avDict, it)
    }
    print("${result.size}/${avDict.size}")
}