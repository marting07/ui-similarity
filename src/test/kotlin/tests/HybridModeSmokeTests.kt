package tests

import corpus.RepoId
import scanner.AngularAstRepoScanner
import scanner.CommandAngularAstEngine
import scanner.CommandReactAstEngine
import scanner.CompositeRepoScanner
import scanner.ReactAstRepoScanner
import scanner.VueRepoScanner
import tests.TestSupport.assertTrue

object HybridModeSmokeTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("Hybrid mode falls back when AST commands fail") {
                val composite = CompositeRepoScanner(
                    listOf(
                        ReactAstRepoScanner(
                            astEngine = CommandReactAstEngine("exit 1"),
                            allowFallback = true
                        ),
                        AngularAstRepoScanner(
                            astEngine = CommandAngularAstEngine("exit 1"),
                            allowFallback = true
                        ),
                        VueRepoScanner()
                    )
                )

                val reactRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "sample-react"),
                    Fixtures.reactRepo
                )
                val angularRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "sample-angular"),
                    Fixtures.angularRepo
                )
                val vueRefs = composite.scanRepo(
                    RepoId("github.com", "acme", "sample-vue"),
                    Fixtures.vueRepo
                )

                assertTrue(reactRefs.isNotEmpty(), "React should fallback to legacy scanner when AST command fails")
                assertTrue(angularRefs.isNotEmpty(), "Angular should fallback to legacy scanner when AST command fails")
                assertTrue(vueRefs.isNotEmpty(), "Vue scanner should continue to work")
            }
        )
    }
}
