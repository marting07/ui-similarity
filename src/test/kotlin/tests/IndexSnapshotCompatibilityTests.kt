package tests

import persistence.IndexSnapshotCompatibility

object IndexSnapshotCompatibilityTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("compatibility report marks v1 loadable and migration required") {
                val json = """{"version":1,"createdAtEpochMs":1,"componentCount":1,"pivotCount":1,"pivotIds":[],"records":[]}"""
                val report = IndexSnapshotCompatibility.assess(json)
                TestSupport.assertEquals(1, report.detectedVersion, "Version should parse")
                TestSupport.assertEquals(true, report.loadSupported, "v1 should be loadable")
                TestSupport.assertEquals(true, report.migrationRequired, "v1 should indicate migration path to v2")
            },
            TestSupport.test("compatibility report marks missing version unsupported") {
                val json = """{"componentCount":1}"""
                val report = IndexSnapshotCompatibility.assess(json)
                TestSupport.assertEquals(false, report.loadSupported, "Missing version is unsupported")
                TestSupport.assertContains(report.message, "Missing required field: version", "Message should explain issue")
            }
        )
    }
}
