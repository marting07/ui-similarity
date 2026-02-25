package tests

import core.model.UiFramework
import persistence.INDEX_SNAPSHOT_VERSION_V1
import persistence.IndexSnapshotIO
import persistence.PersistedPermutation
import persistence.PermutationIndexSnapshotV1
import persistence.SnapshotBuildConfig
import persistence.SnapshotMetadata
import java.nio.file.Files

object IndexSnapshotIOTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("snapshot IO roundtrip preserves metadata and records") {
                val snapshot = sampleSnapshot()
                val file = Files.createTempFile("index-snapshot-", ".json").toFile()
                try {
                    IndexSnapshotIO.save(file, snapshot)
                    val loaded = IndexSnapshotIO.load(file)
                    TestSupport.assertEquals(snapshot, loaded, "Roundtrip should preserve snapshot content")
                } finally {
                    file.delete()
                }
            },
            TestSupport.test("validate reports missing version") {
                val json = """{"componentCount":1,"pivotCount":1,"createdAtEpochMs":1,"pivotIds":[],"records":[]}"""
                val errors = IndexSnapshotIO.validate(json)
                TestSupport.assertTrue(
                    errors.any { it.contains("Missing required field: version") },
                    "Validation should report missing version"
                )
            },
            TestSupport.test("load rejects unsupported snapshot version") {
                val json = """
                    {
                      "version": 99,
                      "createdAtEpochMs": 1730000000000,
                      "componentCount": 1,
                      "pivotCount": 1,
                      "frameworkCounts": {"react":1},
                      "buildConfig": {
                        "extractionMode": "hybrid",
                        "domAstEnabled": true,
                        "cssAstEnabled": false,
                        "behaviorAstEnabled": false,
                        "samplePercent": null,
                        "sampleSeed": null,
                        "sampleMode": null
                      },
                      "pivotIds": ["c-1"],
                      "records": [{"componentId":"c-1","orderedPivotIds":["c-1"]}]
                    }
                """.trimIndent()
                val file = Files.createTempFile("index-snapshot-bad-version-", ".json").toFile()
                try {
                    file.writeText(json)
                    val error = try {
                        IndexSnapshotIO.load(file)
                        null
                    } catch (t: Throwable) {
                        t
                    }
                    TestSupport.assertTrue(error != null, "Unsupported version should fail load")
                    TestSupport.assertContains(
                        error!!.message ?: "",
                        "Unsupported snapshot version",
                        "Error should mention unsupported version"
                    )
                } finally {
                    file.delete()
                }
            }
        )
    }

    private fun sampleSnapshot(): PermutationIndexSnapshotV1 {
        return PermutationIndexSnapshotV1(
            metadata = SnapshotMetadata(
                version = INDEX_SNAPSHOT_VERSION_V1,
                createdAtEpochMs = 1730000000000,
                componentCount = 2,
                pivotCount = 2,
                frameworkCounts = mapOf(UiFramework.REACT to 1, UiFramework.VUE to 1),
                buildConfig = SnapshotBuildConfig(
                    extractionMode = "hybrid",
                    domAstEnabled = true,
                    cssAstEnabled = false,
                    behaviorAstEnabled = true,
                    samplePercent = 10,
                    sampleSeed = 7,
                    sampleMode = "uniform"
                )
            ),
            pivotIds = listOf("c-1", "c-2"),
            records = listOf(
                PersistedPermutation(componentId = "c-1", orderedPivotIds = listOf("c-1", "c-2")),
                PersistedPermutation(componentId = "c-2", orderedPivotIds = listOf("c-2", "c-1"))
            )
        )
    }
}
