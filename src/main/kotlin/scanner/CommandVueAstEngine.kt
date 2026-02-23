package scanner

import corpus.ComponentKey
import corpus.ComponentSourceRef
import corpus.RepoId
import core.model.UiFramework
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class CommandVueAstEngine(
    private val command: String,
    private val timeoutSeconds: Long = 15L
) : VueAstEngine {
    override fun scanRepo(repoId: RepoId, repoRoot: Path): AstScanOutcome {
        val request = VueAstScanRequest(
            repoHost = repoId.host,
            repoOwner = repoId.owner,
            repoName = repoId.name,
            repoRoot = repoRoot.toAbsolutePath().normalize().toString()
        )
        val payload = VueAstContractJson.encodeRequest(request)

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
                return AstScanOutcome.Failure("timeout")
            }

            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) return AstScanOutcome.Failure("command_exit_nonzero")

            val response = VueAstContractJson.decodeResponse(output)
                ?: return AstScanOutcome.Failure("invalid_response")
            if (response.status != "ok") {
                return AstScanOutcome.Failure("status_${response.status}${response.error?.let { "_${it}" } ?: ""}")
            }

            val refs = response.components.map { desc ->
                val relativePath = desc.relativePath.normalizeRelPath()
                ComponentSourceRef(
                    key = ComponentKey(repoId, relativePath, desc.exportName),
                    framework = UiFramework.VUE,
                    repoRoot = repoRoot,
                    templatePath = Path.of((desc.templatePath ?: relativePath).normalizeRelPath()),
                    stylePaths = desc.stylePaths.map { Path.of(it.normalizeRelPath()) },
                    logicPath = Path.of((desc.logicPath ?: relativePath).normalizeRelPath()),
                    inlineTemplateCode = desc.inlineTemplateCode,
                    inlineStyleCodes = desc.inlineStyleCodes
                )
            }
            AstScanOutcome.Success(refs)
        } catch (e: Exception) {
            AstScanOutcome.Failure("exception_${e::class.simpleName ?: "unknown"}")
        }
    }

    private fun String.normalizeRelPath(): String = this.replace('\\', '/')
}

fun createDefaultVueAstEngine(): VueAstEngine {
    val command = System.getenv("UI_SIMILARITY_VUE_AST_CMD")?.takeIf { it.isNotBlank() }
        ?: "node scripts/vue-ast-scan.mjs"
    return CommandVueAstEngine(command)
}
