package pipeline

import core.model.UiFramework
import corpus.RepoId
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Minimal repository descriptor used for deterministic pre-scan sampling.
 */
data class RepoCandidate(
    val repoId: RepoId,
    val framework: UiFramework
)

/**
 * Sampling service shared by CLI and desktop flows.
 */
object RepoSamplingService {
    fun sample(candidates: List<RepoCandidate>, config: SamplingConfig): List<RepoCandidate> {
        if (candidates.isEmpty()) return emptyList()
        require(config.percent in 1..100) { "sample.percent must be in 1..100" }

        val normalized = candidates.sortedBy { it.repoId.toString() }
        return when (config.mode) {
            RepoSamplingMode.GLOBAL -> sampleGlobal(normalized, config.percent, config.seed)
            RepoSamplingMode.STRATIFIED_FRAMEWORK -> sampleStratified(normalized, config.percent, config.seed)
        }
    }

    private fun sampleGlobal(candidates: List<RepoCandidate>, percent: Int, seed: Int): List<RepoCandidate> {
        val target = targetCount(candidates.size, percent)
        return candidates.shuffled(Random(seed)).take(target).sortedBy { it.repoId.toString() }
    }

    private fun sampleStratified(candidates: List<RepoCandidate>, percent: Int, seed: Int): List<RepoCandidate> {
        val grouped = candidates.groupBy { it.framework }
        val selected = mutableListOf<RepoCandidate>()
        for ((framework, repos) in grouped) {
            val target = targetCount(repos.size, percent)
            val frameworkSeed = seed xor (framework.ordinal + 1) * 1_000_003
            selected += repos.shuffled(Random(frameworkSeed)).take(target)
        }
        return selected.sortedBy { it.repoId.toString() }
    }

    private fun targetCount(total: Int, percent: Int): Int {
        if (total <= 0) return 0
        if (percent >= 100) return total
        val raw = ceil(total * (percent / 100.0)).toInt()
        return raw.coerceIn(1, total)
    }
}
