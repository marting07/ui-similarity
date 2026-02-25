package tests

import audit.ScannerAuditRow
import audit.summarizeScannerAuditRows
import tests.TestSupport.assertEquals

object ScannerAuditSummaryTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("summarizeScannerAuditRows aggregates framework totals and mismatch counts") {
                val rows = listOf(
                    ScannerAuditRow(
                        repoId = "github.com/acme/r1",
                        framework = "react",
                        simpleCount = 10,
                        astCount = 9,
                        onlySimple = 2,
                        onlyAst = 1,
                        onlySimpleSample = "id1|id2",
                        onlyAstSample = "idA"
                    ),
                    ScannerAuditRow(
                        repoId = "github.com/acme/r2",
                        framework = "react",
                        simpleCount = 5,
                        astCount = 5,
                        onlySimple = 0,
                        onlyAst = 0,
                        onlySimpleSample = "",
                        onlyAstSample = ""
                    ),
                    ScannerAuditRow(
                        repoId = "github.com/acme/a1",
                        framework = "angular",
                        simpleCount = 12,
                        astCount = 14,
                        onlySimple = 1,
                        onlyAst = 3,
                        onlySimpleSample = "id3",
                        onlyAstSample = "idB|idC"
                    )
                )

                val summary = summarizeScannerAuditRows(rows, topN = 2)
                assertEquals(3, summary.totalRepos, "Total repos should match input")
                assertEquals(2, summary.mismatchRepos, "Mismatch repo count should match rows with deltas")
                assertEquals(2, summary.frameworks.size, "Should include both frameworks")

                val angular = summary.frameworks.first { it.framework == "angular" }
                assertEquals(1, angular.repos, "Angular repo count should match")
                assertEquals(4, angular.onlySimpleTotal + angular.onlyAstTotal, "Angular mismatch total should match")

                val react = summary.frameworks.first { it.framework == "react" }
                assertEquals(2, react.repos, "React repo count should match")
                assertEquals(1, react.mismatchRepos, "React mismatch repo count should match")

                assertEquals(2, summary.topMismatches.size, "Top mismatch list should honor topN")
                assertEquals("github.com/acme/a1", summary.topMismatches.first().repoId, "Largest mismatch should rank first")
            }
        )
    }
}
