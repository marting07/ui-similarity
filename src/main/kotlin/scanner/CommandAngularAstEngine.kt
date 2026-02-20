package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class CommandAngularAstEngine(
    private val command: String,
    private val timeoutSeconds: Long = 15L
) : AngularAstEngine {

    override fun scanRepo(repoId: RepoId, repoRoot: Path): List<ComponentSourceRef>? {
        val request = AngularAstScanRequest(
            repoHost = repoId.host,
            repoOwner = repoId.owner,
            repoName = repoId.name,
            repoRoot = repoRoot.toAbsolutePath().normalize().toString()
        )
        val payload = AngularAstContractJson.encodeRequest(request)

        return try {
            val process = ProcessBuilder("/bin/sh", "-lc", command)
                .redirectErrorStream(true)
                .start()

            process.outputStream.bufferedWriter().use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }

            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) return null

            val response = AngularAstContractJson.decodeResponse(output) ?: return null
            if (response.status != "ok") return null
            response.components.map { desc ->
                val relativePath = desc.relativePath.normalizeRelPath()
                ComponentSourceRef(
                    key = ComponentKey(repoId, relativePath, desc.exportName),
                    framework = UiFramework.ANGULAR,
                    repoRoot = repoRoot,
                    templatePath = Path.of((desc.templatePath ?: relativePath).normalizeRelPath()),
                    stylePaths = desc.stylePaths.map { Path.of(it.normalizeRelPath()) },
                    logicPath = Path.of((desc.logicPath ?: relativePath).normalizeRelPath()),
                    inlineTemplateCode = desc.inlineTemplateCode,
                    inlineStyleCodes = desc.inlineStyleCodes
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun String.normalizeRelPath(): String = this.replace('\\', '/')
}

fun createDefaultAngularAstEngine(): AngularAstEngine {
    val command = System.getenv("UI_SIMILARITY_ANGULAR_AST_CMD")?.takeIf { it.isNotBlank() }
        ?: "node scripts/angular-ast-scan.mjs"
    return CommandAngularAstEngine(command)
}
