import core.model.UiFramework
import extractor.ast.angular.AngularBehaviorAstFailureEvent
import extractor.ast.angular.AngularCssAstFailureEvent
import extractor.ast.angular.AngularDomAstFailureEvent
import extractor.ast.react.ReactBehaviorAstFailureEvent
import extractor.ast.react.ReactDomAstFailureEvent
import extractor.ast.react.ReactCssAstFailureEvent
import extractor.ast.vue.VueBehaviorAstFailureEvent
import extractor.ast.vue.VueCssAstFailureEvent
import extractor.ast.vue.VueDomAstFailureEvent
import scanner.AstScanFailureEvent
import tests.TestSupport
import tests.TestSupport.assertContains
import tests.TestSupport.assertEquals

object AstFailureSummaryTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("formatAstFailureSummary returns no-failure message") {
                val text = formatAstFailureSummary(emptyList())
                assertEquals("AST fallback summary: no AST scanner failures.", text, "Empty telemetry should use no-failure message")
            },
            TestSupport.test("formatAstFailureSummary groups by framework and reason") {
                val events = listOf(
                    AstScanFailureEvent(UiFramework.REACT, "github.com/acme/r1", "command_exit_nonzero", true),
                    AstScanFailureEvent(UiFramework.REACT, "github.com/acme/r2", "timeout", true),
                    AstScanFailureEvent(UiFramework.REACT, "github.com/acme/r2", "timeout", false),
                    AstScanFailureEvent(UiFramework.ANGULAR, "github.com/acme/a1", "invalid_response", true)
                )
                val text = formatAstFailureSummary(events)
                assertContains(text, "AST fallback summary:", "Should include header")
                assertContains(text, "angular: failures=1", "Should include angular summary")
                assertContains(text, "react: failures=3", "Should include react summary")
                assertContains(text, "- timeout: 2", "Should include reason counts")
                assertContains(text, "fallback_used=2, strict_drop=1", "Should include fallback/strict counts")
                assertContains(text, "* repo=github.com/acme/r2 failures=2 top_reason=timeout", "Should include per-repo rollup")
            },
            TestSupport.test("formatReactDomParitySummary shows mismatch breakdown") {
                val events = listOf(
                    ReactDomParityEvent("a", listOf("tagHistogram", "depth")),
                    ReactDomParityEvent("b", listOf("tagHistogram"))
                )
                val text = formatReactDomParitySummary(events, totalReactComponents = 3)
                assertContains(text, "compared=3 mismatches=2", "Should include compared/mismatch counts")
                assertContains(text, "- tagHistogram: 2", "Should aggregate field counts")
                assertContains(text, "* component=a fields=tagHistogram|depth", "Should include component sample")
            },
            TestSupport.test("formatReactDomAstFailureSummary shows reason and component rollup") {
                val events = listOf(
                    ReactDomAstFailureEvent("a", "timeout", true),
                    ReactDomAstFailureEvent("b", "timeout", true),
                    ReactDomAstFailureEvent("c", "invalid_response", true)
                )
                val text = formatReactDomAstFailureSummary(events)
                assertContains(text, "failures=3, components=3", "Should include failure totals")
                assertContains(text, "- timeout: 2", "Should include reason counts")
                assertContains(text, "* component=a reason=timeout fallback_used=true", "Should include component details")
            },
            TestSupport.test("formatReactCss summaries show parity and failure rollups") {
                val parityText = formatReactCssParitySummary(
                    listOf(
                        ReactCssParityEvent("a", listOf("styleTokens", "palette")),
                        ReactCssParityEvent("b", listOf("styleTokens"))
                    ),
                    totalReactComponents = 4
                )
                assertContains(parityText, "compared=4 mismatches=2", "Should include parity counts")
                assertContains(parityText, "- styleTokens: 2", "Should aggregate CSS diff fields")

                val failureText = formatReactCssAstFailureSummary(
                    listOf(
                        ReactCssAstFailureEvent("a", "timeout", true),
                        ReactCssAstFailureEvent("b", "timeout", true),
                        ReactCssAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(failureText, "failures=3, components=3", "Should include CSS failure totals")
                assertContains(failureText, "- timeout: 2", "Should include CSS reason counts")
            },
            TestSupport.test("formatReactBehavior summaries show parity and failure rollups") {
                val parityText = formatReactBehaviorParitySummary(
                    listOf(
                        ReactBehaviorParityEvent("a", listOf("eventTypes", "cyclomatic")),
                        ReactBehaviorParityEvent("b", listOf("eventTypes"))
                    ),
                    totalReactComponents = 4
                )
                assertContains(parityText, "compared=4 mismatches=2", "Should include behavior parity counts")
                assertContains(parityText, "- eventTypes: 2", "Should aggregate behavior diff fields")

                val failureText = formatReactBehaviorAstFailureSummary(
                    listOf(
                        ReactBehaviorAstFailureEvent("a", "timeout", true),
                        ReactBehaviorAstFailureEvent("b", "timeout", true),
                        ReactBehaviorAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(failureText, "failures=3, components=3", "Should include behavior failure totals")
                assertContains(failureText, "- timeout: 2", "Should include behavior reason counts")
            },
            TestSupport.test("formatAngularDomAstFailureSummary shows reason and component rollup") {
                val text = formatAngularDomAstFailureSummary(
                    listOf(
                        AngularDomAstFailureEvent("a", "timeout", true),
                        AngularDomAstFailureEvent("b", "timeout", true),
                        AngularDomAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(text, "failures=3, components=3", "Should include Angular DOM failure totals")
                assertContains(text, "- timeout: 2", "Should include Angular DOM reason counts")
                assertContains(text, "* component=a reason=timeout fallback_used=true", "Should include component details")
            },
            TestSupport.test("formatAngularCssAstFailureSummary shows reason and component rollup") {
                val text = formatAngularCssAstFailureSummary(
                    listOf(
                        AngularCssAstFailureEvent("a", "timeout", true),
                        AngularCssAstFailureEvent("b", "timeout", true),
                        AngularCssAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(text, "failures=3, components=3", "Should include Angular CSS failure totals")
                assertContains(text, "- timeout: 2", "Should include Angular CSS reason counts")
                assertContains(text, "* component=a reason=timeout fallback_used=true", "Should include component details")
            },
            TestSupport.test("formatAngularBehaviorAstFailureSummary shows reason and component rollup") {
                val text = formatAngularBehaviorAstFailureSummary(
                    listOf(
                        AngularBehaviorAstFailureEvent("a", "timeout", true),
                        AngularBehaviorAstFailureEvent("b", "timeout", true),
                        AngularBehaviorAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(text, "failures=3, components=3", "Should include Angular behavior failure totals")
                assertContains(text, "- timeout: 2", "Should include Angular behavior reason counts")
                assertContains(text, "* component=a reason=timeout fallback_used=true", "Should include component details")
            },
            TestSupport.test("formatVue DOM/CSS failure summaries show reason and component rollup") {
                val domText = formatVueDomAstFailureSummary(
                    listOf(
                        VueDomAstFailureEvent("a", "timeout", true),
                        VueDomAstFailureEvent("b", "timeout", true),
                        VueDomAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(domText, "failures=3, components=3", "Should include Vue DOM failure totals")
                assertContains(domText, "- timeout: 2", "Should include Vue DOM reason counts")

                val cssText = formatVueCssAstFailureSummary(
                    listOf(
                        VueCssAstFailureEvent("a", "timeout", true),
                        VueCssAstFailureEvent("b", "timeout", true),
                        VueCssAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(cssText, "failures=3, components=3", "Should include Vue CSS failure totals")
                assertContains(cssText, "- timeout: 2", "Should include Vue CSS reason counts")
            },
            TestSupport.test("formatVueBehaviorAstFailureSummary shows reason and component rollup") {
                val text = formatVueBehaviorAstFailureSummary(
                    listOf(
                        VueBehaviorAstFailureEvent("a", "timeout", true),
                        VueBehaviorAstFailureEvent("b", "timeout", true),
                        VueBehaviorAstFailureEvent("c", "status_error", true)
                    )
                )
                assertContains(text, "failures=3, components=3", "Should include Vue behavior failure totals")
                assertContains(text, "- timeout: 2", "Should include Vue behavior reason counts")
                assertContains(text, "* component=a reason=timeout fallback_used=true", "Should include component details")
            },
            TestSupport.test("formatExtractorParitySummary handles empty comparisons") {
                val text = formatExtractorParitySummary(emptyMap(), emptyList())
                assertEquals(
                    "Extractor parity summary: no AST extractor comparisons run.",
                    text,
                    "Empty extractor parity should use no-comparison message"
                )
            },
            TestSupport.test("formatExtractorParitySummary groups by framework and layer") {
                val compared = mapOf(
                    ExtractorParityKey(UiFramework.REACT, "dom") to 3,
                    ExtractorParityKey(UiFramework.VUE, "css") to 2
                )
                val events = listOf(
                    ExtractorParityEvent(UiFramework.REACT, "dom", "react#a", listOf("tagHistogram", "depth")),
                    ExtractorParityEvent(UiFramework.REACT, "dom", "react#b", listOf("tagHistogram")),
                    ExtractorParityEvent(UiFramework.VUE, "css", "vue#a", listOf("styleTokens"))
                )
                val text = formatExtractorParitySummary(compared, events)
                assertContains(text, "react.dom: compared=3 mismatches=2", "Should include react.dom rollup")
                assertContains(text, "vue.css: compared=2 mismatches=1", "Should include vue.css rollup")
                assertContains(text, "- tagHistogram: 2", "Should aggregate mismatch fields")
                assertContains(text, "* component=react#a fields=tagHistogram|depth", "Should include component sample")
            }
        )
    }
}
