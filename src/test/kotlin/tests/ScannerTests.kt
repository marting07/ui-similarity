package tests

import corpus.RepoId
import core.model.UiFramework
import extractor.SourceLoader
import scanner.AngularRepoScanner
import scanner.AngularAstRepoScanner
import scanner.CompositeRepoScanner
import scanner.NoopReactAstEngine
import scanner.NoopAngularAstEngine
import scanner.ReactAstRepoScanner
import scanner.ReactRepoScanner
import scanner.VueRepoScanner
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object ScannerTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("react scanner discovers one component with style") {
                val repoId = RepoId("github.com", "acme", "sample-react")
                val refs = ReactRepoScanner().scanRepo(repoId, Fixtures.reactRepo)
                assertEquals(1, refs.size, "React scanner should find one exported component")
                assertEquals(UiFramework.REACT, refs[0].framework, "Framework should be React")
                assertTrue(refs[0].stylePaths.isNotEmpty(), "React component should include discovered style path")
                assertTrue(refs[0].key.id.contains("#Button"), "Component id should include export name")
            },
            TestSupport.test("angular scanner resolves template and styles") {
                val repoId = RepoId("github.com", "acme", "sample-angular")
                val refs = AngularRepoScanner().scanRepo(repoId, Fixtures.angularRepo)
                assertEquals(1, refs.size, "Angular scanner should find one component")
                val templatePath = refs[0].templatePath.toString().replace('\\', '/')
                assertTrue(
                    templatePath.endsWith("src/app/app.component.html"),
                    "Angular template path should come from templateUrl"
                )
                assertEquals(1, refs[0].stylePaths.size, "Angular scanner should find one style path")
            },
            TestSupport.test("vue scanner discovers single-file component") {
                val repoId = RepoId("github.com", "acme", "sample-vue")
                val refs = VueRepoScanner().scanRepo(repoId, Fixtures.vueRepo)
                assertEquals(1, refs.size, "Vue scanner should find one .vue component")
                assertEquals("HelloCard", refs[0].key.exportName, "Vue export name should default to file stem")
                assertEquals(1, refs[0].stylePaths.size, "Vue style path should point to same .vue file")
            },
            TestSupport.test("composite scanner detects framework from root files") {
                val scanners = listOf(ReactRepoScanner(), AngularRepoScanner(), VueRepoScanner())
                val composite = CompositeRepoScanner(scanners)
                val reactRefs = composite.scanRepo(RepoId("github.com", "acme", "sample-react"), Fixtures.reactRepo)
                val angularRefs = composite.scanRepo(RepoId("github.com", "acme", "sample-angular"), Fixtures.angularRepo)
                val vueRefs = composite.scanRepo(RepoId("github.com", "acme", "sample-vue"), Fixtures.vueRepo)
                assertEquals(1, reactRefs.size, "Composite scanner should route to React scanner")
                assertEquals(1, angularRefs.size, "Composite scanner should route to Angular scanner")
                assertEquals(1, vueRefs.size, "Composite scanner should route to Vue scanner")
            },
            TestSupport.test("react scanner should not treat keyword as default export name") {
                val repoId = RepoId("github.com", "acme", "edge-react")
                val refs = ReactRepoScanner().scanRepo(repoId, Fixtures.reactEdgeRepo)
                assertEquals(1, refs.size, "React scanner should detect one default-exported component")
                assertEquals("FancyCard", refs[0].key.exportName, "Default export function name should be captured correctly")
            },
            TestSupport.test("angular scanner captures inline template and styles for non-standard class suffix") {
                val repoId = RepoId("github.com", "acme", "edge-angular")
                val refs = AngularRepoScanner().scanRepo(repoId, Fixtures.angularEdgeRepo)
                assertEquals(1, refs.size, "Angular scanner should detect class even without Component suffix")
                assertEquals("FancyCard", refs[0].key.exportName, "Angular class name should be used as export name")
                val source = SourceLoader.load(refs[0])
                assertTrue(source.templateCode.contains("<section"), "Inline template should be loaded as template code")
                assertTrue(source.styleCode.contains("margin: 6px"), "Inline styles should be loaded as style code")
            },
            TestSupport.test("react AST scanner strict mode does not fallback when engine unavailable") {
                val repoId = RepoId("github.com", "acme", "sample-react")
                val scanner = ReactAstRepoScanner(astEngine = NoopReactAstEngine, allowFallback = false)
                val refs = scanner.scanRepo(repoId, Fixtures.reactRepo)
                assertEquals(0, refs.size, "Strict AST mode should not fallback when AST engine is unavailable")
            },
            TestSupport.test("react AST scanner hybrid mode falls back when engine unavailable") {
                val repoId = RepoId("github.com", "acme", "sample-react")
                val scanner = ReactAstRepoScanner(astEngine = NoopReactAstEngine, allowFallback = true)
                val refs = scanner.scanRepo(repoId, Fixtures.reactRepo)
                assertTrue(refs.isNotEmpty(), "Hybrid mode should fallback to legacy scanner")
            },
            TestSupport.test("angular AST scanner strict mode does not fallback when engine unavailable") {
                val repoId = RepoId("github.com", "acme", "sample-angular")
                val scanner = AngularAstRepoScanner(astEngine = NoopAngularAstEngine, allowFallback = false)
                val refs = scanner.scanRepo(repoId, Fixtures.angularRepo)
                assertEquals(0, refs.size, "Strict AST mode should not fallback when AST engine is unavailable")
            },
            TestSupport.test("angular AST scanner hybrid mode falls back when engine unavailable") {
                val repoId = RepoId("github.com", "acme", "sample-angular")
                val scanner = AngularAstRepoScanner(astEngine = NoopAngularAstEngine, allowFallback = true)
                val refs = scanner.scanRepo(repoId, Fixtures.angularRepo)
                assertTrue(refs.isNotEmpty(), "Hybrid mode should fallback to legacy scanner")
            }
        )
    }
}
