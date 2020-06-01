package kr3v.webdetect

import com.fasterxml.jackson.annotation.JsonIgnore

sealed class AppVersion {
    abstract fun apps(): List<String>
    abstract fun versions(): List<String>
    abstract fun appVersions(): List<AppVersion.Single>

    data class Single constructor(
        val app: String,
        val version: String,
        @field:JsonIgnore
        val both: String = app + version
    ) : AppVersion(), Comparable<AppVersion.Single> {
        override fun apps(): List<String> = listOf(app)
        override fun versions(): List<String> = listOf(version)
        override fun appVersions(): List<AppVersion.Single> = listOf(this)
        override fun equals(other: Any?) = other === this || other is AppVersion.Single && other.both == both
        override fun hashCode() = both.hashCode()
        override fun toString() = "$app#$version"
        override fun compareTo(other: AppVersion.Single) = both.compareTo(other.both)
    }

    data class Merged(
        val appVersions: FMutableSet<AppVersion>
    ) : AppVersion() {
        override fun apps(): List<String> = appVersions.flatMap { it.apps() }
        override fun versions(): List<String> = appVersions.flatMap { it.versions() }
        override fun appVersions(): List<AppVersion.Single> = appVersions.flatMap { it.appVersions() }
        override fun toString(): String = appVersions.toString()
    }
}
