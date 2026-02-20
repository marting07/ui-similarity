package tests

import corpus.RepoId
import scanner.AngularAstContractJson
import scanner.AngularAstScanRequest
import scanner.CommandAngularAstEngine
import tests.TestSupport.assertContains
import tests.TestSupport.assertEquals
import tests.TestSupport.assertTrue

object AngularAstContractTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("angular AST contract encodes request as JSON") {
                val json = AngularAstContractJson.encodeRequest(
                    AngularAstScanRequest(
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
            TestSupport.test("angular AST contract decodes component response") {
                val responseJson = """
                    {
                      "status": "ok",
                      "components": [
                        {
                          "relativePath": "src/app/card.component.ts",
                          "exportName": "Card",
                          "templatePath": "src/app/card.component.html",
                          "logicPath": "src/app/card.component.ts",
                          "stylePaths": ["src/app/card.component.css"],
                          "inlineTemplateCode": "<div>Card</div>",
                          "inlineStyleCodes": [".x { margin: 4px; }"]
                        }
                      ]
                    }
                """.trimIndent()
                val response = AngularAstContractJson.decodeResponse(responseJson)
                assertEquals("ok", response?.status, "Status should decode")
                assertEquals(1, response?.components?.size, "One component should decode")
                val component = response!!.components.first()
                assertEquals("Card", component.exportName, "Export name should decode")
                assertTrue(component.inlineTemplateCode?.contains("Card") == true, "Inline template should decode")
            },
            TestSupport.test("command Angular AST engine maps JSON output to ComponentSourceRef") {
                val command = "cat <<'JSON'\n{\"status\":\"ok\",\"components\":[{\"relativePath\":\"src/app/Widget.component.ts\",\"exportName\":\"Widget\",\"templatePath\":\"src/app/Widget.component.html\",\"stylePaths\":[\"src/app/Widget.component.css\"]}]}\nJSON"
                val engine = CommandAngularAstEngine(command)
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "repo"),
                    Fixtures.angularRepo
                )
                assertEquals(1, refs?.size, "Engine should return one mapped source ref")
                val ref = refs!!.first()
                assertEquals("Widget", ref.key.exportName, "Export name should map to key")
                assertEquals("src/app/Widget.component.ts", ref.logicPath.toString().replace('\\', '/'), "Logic path should map")
                assertEquals(1, ref.stylePaths.size, "Style path list should map")
            },
            TestSupport.test("real node Angular AST command extracts inline component from fixture repo") {
                val engine = CommandAngularAstEngine("node scripts/angular-ast-scan.mjs")
                val refs = engine.scanRepo(
                    RepoId("github.com", "acme", "edge-angular"),
                    Fixtures.angularEdgeRepo
                )
                assertEquals(1, refs?.size, "Node Angular AST command should extract one component")
                val ref = refs!!.first()
                assertEquals("FancyCard", ref.key.exportName, "Inline component class should be parsed")
                assertTrue(ref.inlineTemplateCode?.contains("<section") == true, "Inline template should map")
                assertTrue(ref.inlineStyleCodes.any { it.contains("margin: 6px") }, "Inline styles should map")
            }
        )
    }
}
