package persistence

/**
 * Compatibility policy for snapshot files across schema versions.
 *
 * Current state:
 * - v1 can be loaded by runtime.
 * - v2 is the planned target for future migration (not yet loadable).
 */
object IndexSnapshotCompatibility {
    const val LATEST_TARGET_VERSION: Int = 2
    private val loadSupportedVersions = setOf(INDEX_SNAPSHOT_VERSION_V1)

    data class Report(
        val detectedVersion: Int?,
        val loadSupported: Boolean,
        val migrationRequired: Boolean,
        val message: String
    )

    fun assess(json: String): Report {
        val version = detectVersion(json)
        if (version == null) {
            return Report(
                detectedVersion = null,
                loadSupported = false,
                migrationRequired = true,
                message = "Missing required field: version"
            )
        }
        if (version in loadSupportedVersions) {
            val migrationRequired = version < LATEST_TARGET_VERSION
            val message = if (migrationRequired) {
                "Snapshot v$version is loadable; migration path exists to v$LATEST_TARGET_VERSION."
            } else {
                "Snapshot v$version is loadable and current."
            }
            return Report(
                detectedVersion = version,
                loadSupported = true,
                migrationRequired = migrationRequired,
                message = message
            )
        }
        return Report(
            detectedVersion = version,
            loadSupported = false,
            migrationRequired = true,
            message = "Snapshot v$version is not loadable by current runtime."
        )
    }

    /**
     * Migration utility for today: normalizes v1 JSON by parsing and writing
     * through the canonical serializer. This is a stable base for a future
     * v1->v2 structural migration.
     */
    fun normalizeV1(json: String): String {
        val snapshot = IndexSnapshotIO.loadFromString(json)
        return IndexSnapshotIO.toJson(snapshot)
    }

    private fun detectVersion(json: String): Int? {
        val regex = Regex("\"version\"\\s*:\\s*([0-9]+)")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }
}
