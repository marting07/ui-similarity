package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.vue.VueAstDomFeatureExtractor
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object VueAstDomExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("vue AST DOM extractor extracts tags roles and shape") {
                val extractor = VueAstDomFeatureExtractor(command = "node scripts/vue-dom-ast-extract.mjs")
                val source = ComponentSource(
                    id = "vue#ast-dom",
                    framework = UiFramework.VUE,
                    templateCode = """
                        <section role="main" class="grid cards">
                          <ul>
                            <li v-for="item in items" :key="item.id">{{ item.name }}</li>
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
            TestSupport.test("vue AST DOM extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = VueAstDomFeatureExtractor(command = "exit 1")
                val extractorWithTelemetry = VueAstDomFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "vue#fallback",
                    framework = UiFramework.VUE,
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
