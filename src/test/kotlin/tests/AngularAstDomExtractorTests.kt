package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.angular.AngularAstDomFeatureExtractor
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object AngularAstDomExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("angular AST DOM extractor extracts tags roles and shape") {
                val extractor = AngularAstDomFeatureExtractor(command = "node scripts/angular-dom-ast-extract.mjs")
                val source = ComponentSource(
                    id = "angular#ast-dom",
                    framework = UiFramework.ANGULAR,
                    templateCode = """
                        <section role="main" class="grid cards">
                          <ul>
                            <li *ngFor="let item of items">{{ item.name }}</li>
                          </ul>
                        </section>
                    """.trimIndent(),
                    styleCode = "",
                    logicCode = ""
                )
                val dom = extractor.extractDomFeatures(source)
                assertEquals(1, dom.tagHistogram["section"], "Should count section")
                assertEquals(1, dom.tagHistogram["ul"], "Should count ul")
                assertEquals(1, dom.tagHistogram["li"], "Should count li")
                assertEquals(1, dom.roleHistogram["main"], "Should count role")
                assertTrue(dom.layoutPatterns.contains("list-vertical"), "Should infer list layout")
                assertTrue(dom.depth >= 2, "Depth should reflect nesting")
            },
            TestSupport.test("angular AST DOM extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = AngularAstDomFeatureExtractor(command = "exit 1")
                val extractorWithTelemetry = AngularAstDomFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "angular#fallback",
                    framework = UiFramework.ANGULAR,
                    templateCode = "<div role=\"dialog\"><span>X</span></div>",
                    styleCode = "",
                    logicCode = ""
                )
                val dom = extractor.extractDomFeatures(source)
                extractorWithTelemetry.extractDomFeatures(source)
                assertEquals(1, dom.tagHistogram["div"], "Fallback extractor should still produce div tag")
                assertEquals(1, dom.roleHistogram["dialog"], "Fallback extractor should still detect role")
                assertTrue(failures.isNotEmpty(), "Failure telemetry should capture fallback reason")
            }
        )
    }
}
