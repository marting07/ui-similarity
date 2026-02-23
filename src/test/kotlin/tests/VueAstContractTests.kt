package tests

import corpus.RepoId
import scanner.AstScanOutcome
import scanner.CommandVueAstEngine
import scanner.VueAstContractJson
import scanner.VueAstScanRequest
import tests.TestSupport.assertContains
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object VueAstContractTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("vue AST contract encodes request as JSON") {
                val json = VueAstContractJson.encodeRequest(
                    VueAstScanRequest(
                        repoHost = "github.com",
                        repoOwner = "acme",
                        repoName = "repo",
                        repoRoot = "/tmp/repo"
                    )
                )
                assertContains(json, "\"repoHost\":\"github.com\"", "Request should include repoHost")
                assertContains(json, "\"repoOwner\":\"acme\"", "Request should include repoOwner")
                assertContains(json, "\"repoName\":\"repo\"", "Request should include repoName")
                assertContains(json, "\"repoRoot\":\"/tmp/repo\"", "Request should include repoRoot")
            },
            TestSupport.test("vue AST contract decodes component response") {
                val responseJson = """
                    {
                      "status": "ok",
                      "components": [
                        {
                          "relativePath": "src/HelloCard.vue",
                          "exportName": "HelloCard",
                          "templatePath": "src/HelloCard.vue",
                          "logicPath": "src/HelloCard.vue",
                          "stylePaths": [],
                          "inlineTemplateCode": "<div>Hello</div>",
                          "inlineStyleCodes": [".card { border-radius: 8px; }"]
                        }
                      ]
                    }
                """.trimIndent()
                val response = VueAstContractJson.decodeResponse(responseJson)
                assertEquals("ok", response?.status, "Status should decode")
                assertEquals(1, response?.components?.size, "One component should decode")
                val component = response!!.components.first()
                assertEquals("HelloCard", component.exportName, "Export name should decode")
                assertTrue(component.inlineTemplateCode?.contains("Hello") == true, "Inline template should decode")
            },
            TestSupport.test("command Vue AST engine maps JSON output to ComponentSourceRef") {
                val command = "cat <<'JSON'\n{\"status\":\"ok\",\"components\":[{\"relativePath\":\"src/Widget.vue\",\"exportName\":\"Widget\",\"inlineStyleCodes\":[\".x{}\"]}]}\nJSON"
                val engine = CommandVueAstEngine(command)
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "repo"),
                    Fixtures.vueRepo
                )
                assertTrue(refs is AstScanOutcome.Success, "Engine should return success outcome")
                val ref = (refs as AstScanOutcome.Success).refs.first()
                assertEquals("Widget", ref.key.exportName, "Export name should map to key")
                assertEquals("src/Widget.vue", ref.logicPath.toString().replace('\\', '/'), "Logic path should map")
                assertEquals(1, ref.inlineStyleCodes.size, "Inline style list should map")
            },
            TestSupport.test("real node Vue AST command extracts SFC from fixture repo") {
                val engine = CommandVueAstEngine("node scripts/vue-ast-scan.mjs")
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "sample-vue"),
                    Fixtures.vueRepo
                )
                assertTrue(refs is AstScanOutcome.Success, "Node Vue AST command should return success outcome")
                val components = (refs as AstScanOutcome.Success).refs
                assertEquals(1, components.size, "Node Vue AST command should extract one component")
                val ref = components.first()
                assertEquals("HelloCard", ref.key.exportName, "Vue file stem should be parsed")
                assertTrue(ref.inlineTemplateCode?.contains("<section") == true, "Template block should map")
                assertTrue(ref.inlineStyleCodes.any { it.contains("border-radius") }, "Style block should map")
            }
        )
    }
}
