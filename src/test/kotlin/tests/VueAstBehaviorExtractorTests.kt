package tests

import core.model.UiFramework
import extractor.ComponentSource
import extractor.ast.vue.VueAstBehaviorFeatureExtractor
import tests.TestSupport.assertTrue

object VueAstBehaviorExtractorTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("vue AST behavior extractor extracts events state apis and complexity") {
                val extractor = VueAstBehaviorFeatureExtractor(command = "node scripts/vue-behavior-ast-extract.mjs")
                val source = ComponentSource(
                    id = "vue#ast-behavior",
                    framework = UiFramework.VUE,
                    templateCode = "",
                    styleCode = "",
                    logicCode = """
                        const open = ref(false)
                        function onClick() {
                          if (open.value) {
                            axios.get('/x')
                          } else {
                            fetch('/y')
                          }
                          open.value = !open.value
                        }
                    """.trimIndent()
                )
                val behavior = extractor.extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Should detect click event")
                assertTrue(behavior.apiSignatures.any { it.startsWith("axios") }, "Should detect axios usage")
                assertTrue(behavior.apiSignatures.any { it.startsWith("fetch") }, "Should detect fetch usage")
                assertTrue(behavior.cyclomatic > 1, "Cyclomatic should be > 1")
            },
            TestSupport.test("vue AST behavior extractor falls back when command fails") {
                val failures = mutableListOf<String>()
                val extractor = VueAstBehaviorFeatureExtractor(
                    command = "exit 1",
                    onFailure = { failures += it.reason }
                )
                val source = ComponentSource(
                    id = "vue#behavior-fallback",
                    framework = UiFramework.VUE,
                    templateCode = "",
                    styleCode = "",
                    logicCode = "function onClick() { fetch('/x') }"
                )
                val behavior = extractor.extractBehaviorFeatures(source)
                assertTrue(behavior.eventTypes.contains("click"), "Fallback extractor should still detect click")
                assertTrue(failures.isNotEmpty(), "Failure telemetry should capture fallback reason")
            }
        )
    }
}
