package com.codeup.migrations

interface Migration {
    val fromVersion: Int
    val toVersion: Int
    fun migrate(prev: Any): Any
}

data class MigrationResult<T>(
    val value: T,
    val migrated: Boolean,
    val appliedSteps: List<Int>,
)

class SchemaTooNewError(val found: Int, val current: Int) :
    Exception("file schemaVersion $found is newer than this build supports ($current)")

@Suppress("UNCHECKED_CAST")
fun <T> runMigrations(
    raw: Any?,
    artifactName: String,
    currentVersion: Int,
    registry: List<Migration>,
): MigrationResult<T> {
    if (raw == null || raw !is Map<*, *>) {
        throw IllegalArgumentException("$artifactName: cannot migrate, not an object")
    }
    val r = raw as Map<String, Any?>
    val found = (r["schemaVersion"] as? Number)?.toInt() ?: 1
    if (found > currentVersion) throw SchemaTooNewError(found, currentVersion)

    var cur: Any = raw
    val appliedSteps = mutableListOf<Int>()
    for (v in found until currentVersion) {
        val step = registry.find { it.fromVersion == v }
            ?: throw IllegalArgumentException("$artifactName: no migration registered from v$v to v${v + 1}")
        val migrated = step.migrate(cur)
        val migratedMap = (migrated as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<String, Any?>()
        @Suppress("UNCHECKED_CAST")
        (migratedMap as MutableMap<String, Any?>)["schemaVersion"] = step.toVersion
        cur = migratedMap
        appliedSteps.add(step.toVersion)
    }
    return MigrationResult(cur as T, appliedSteps.isNotEmpty(), appliedSteps)
}

// Current versions and migration registries — add migrations here when bumping schemas
const val FINDING_CURRENT_VERSION = 1
val FINDING_MIGRATIONS: List<Migration> = emptyList()

const val DISMISSAL_CURRENT_VERSION = 1
val DISMISSAL_MIGRATIONS: List<Migration> = emptyList()

const val EXEMPLAR_CURRENT_VERSION = 1
val EXEMPLAR_MIGRATIONS: List<Migration> = emptyList()

const val CUSTOM_PATTERNS_CURRENT_VERSION = 1
val CUSTOM_PATTERNS_MIGRATIONS: List<Migration> = emptyList()