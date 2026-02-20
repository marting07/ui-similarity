package tests

import corpus.RepoId
import scanner.CompositeRepoScanner
import scanner.ExtractionMode
import scanner.createFrameworkScanners
import tests.TestSupport.assertTrue

object AstModeSmokeTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("AST mode scanner factory extracts React and Angular fixtures") {
                val composite = CompositeRepoScanner(createFrameworkScanners(ExtractionMode.AST))

                val reactRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "edge-react"),
                    Fixtures.reactEdgeRepo
                )
                val angularRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "edge-angular"),
                    Fixtures.angularEdgeRepo
                )
                val vueRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "sample-vue"),
                    Fixtures.vueRepo
                )

                assertTrue(reactRefs.isNotEmpty(), "AST mode should extract React components via AST bridge")
                assertTrue(angularRefs.isNotEmpty(), "AST mode should extract Angular components via AST bridge")
                assertTrue(vueRefs.isNotEmpty(), "AST mode should still extract Vue components")
            }
        )
    }
}
