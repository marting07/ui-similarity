package tests

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import extractor.SourceLoader
import tests.TestSupport.assertContains
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object SourceLoaderTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("source loader reads template logic and concatenated styles") {
                val sourceRef = ComponentSourceRef(
                    key = ComponentKey(RepoId("github.com", "acme", "fixture"), "template.html", "Template"),
                    framework = UiFramework.REACT,
                    repoRoot = Fixtures.loaderTemplate.parent,
                    templatePath = Fixtures.loaderTemplate.fileName,
                    stylePaths = listOf(
                        Fixtures.loaderStyleA.fileName,
                        Fixtures.loaderStyleB.fileName
                    ),
                    logicPath = Fixtures.loaderLogic.fileName
                )
                val source = SourceLoader.load(sourceRef)
                assertContains(source.templateCode, "role=\"dialog\"", "Template should be loaded")
                assertContains(source.styleCode, "margin: 10px", "First style should be loaded")
                assertContains(source.styleCode, "padding: 4px", "Second style should be loaded")
                assertContains(source.logicCode, "fetch('/api')", "Logic should be loaded")
            },
            TestSupport.test("source loader returns empty text for missing files") {
                val sourceRef = ComponentSourceRef(
                    key = ComponentKey(RepoId("github.com", "acme", "fixture"), "missing.html", "Missing"),
                    framework = UiFramework.REACT,
                    repoRoot = Fixtures.loaderTemplate.parent,
                    templatePath = Fixtures.loaderMissing.fileName,
                    stylePaths = listOf(Fixtures.loaderMissing.fileName),
                    logicPath = Fixtures.loaderMissing.fileName
                )
                val source = SourceLoader.load(sourceRef)
                assertTrue(source.templateCode.isEmpty(), "Missing template should be empty")
                assertTrue(source.styleCode.isEmpty(), "Missing styles should be empty")
                assertEquals("", source.logicCode, "Missing logic should be empty")
            }
        )
    }
}
