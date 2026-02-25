package tests

import core.model.UiFramework
import corpus.RepoId
import pipeline.RepoCandidate
import pipeline.RepoSamplingMode
import pipeline.RepoSamplingService
import pipeline.SamplingConfig

object RepoSamplingServiceTests {
    fun run(): List<Pair<String, Throwable?>> {
        return listOf(
            TestSupport.test("global sampling is deterministic with fixed seed") {
                val repos = (1..10).map { idx ->
                    RepoCandidate(
                        repoId = RepoId("github.com", "acme", "repo-$idx"),
                        framework = UiFramework.REACT
                    )
                }
                val config = SamplingConfig(percent = 30, seed = 42, mode = RepoSamplingMode.GLOBAL)
                val a = RepoSamplingService.sample(repos, config)
                val b = RepoSamplingService.sample(repos, config)

                TestSupport.assertEquals(3, a.size, "30% of 10 repos should sample 3")
                TestSupport.assertEquals(
                    a.map { it.repoId.toString() },
                    b.map { it.repoId.toString() },
                    "Sampling should be deterministic with same input and seed"
                )
            },
            TestSupport.test("global sampling keeps at least one repo for non-empty corpus") {
                val repos = listOf(
                    RepoCandidate(RepoId("github.com", "acme", "r1"), UiFramework.REACT),
                    RepoCandidate(RepoId("github.com", "acme", "r2"), UiFramework.REACT)
                )
                val sampled = RepoSamplingService.sample(
                    repos,
                    SamplingConfig(percent = 1, seed = 7, mode = RepoSamplingMode.GLOBAL)
                )
                TestSupport.assertEquals(1, sampled.size, "Low positive percent should still keep one repo")
            },
            TestSupport.test("stratified framework sampling preserves per-framework proportions") {
                val reactRepos = (1..5).map { idx ->
                    RepoCandidate(RepoId("github.com", "acme", "react-$idx"), UiFramework.REACT)
                }
                val vueRepos = (1..5).map { idx ->
                    RepoCandidate(RepoId("github.com", "acme", "vue-$idx"), UiFramework.VUE)
                }
                val all = (reactRepos + vueRepos).reversed()
                val config = SamplingConfig(percent = 40, seed = 11, mode = RepoSamplingMode.STRATIFIED_FRAMEWORK)

                val sampled = RepoSamplingService.sample(all, config)
                val reactCount = sampled.count { it.framework == UiFramework.REACT }
                val vueCount = sampled.count { it.framework == UiFramework.VUE }

                TestSupport.assertEquals(2, reactCount, "Should sample 40% of React repos")
                TestSupport.assertEquals(2, vueCount, "Should sample 40% of Vue repos")

                val sampledRepeat = RepoSamplingService.sample(all, config)
                TestSupport.assertEquals(
                    sampled.map { it.repoId.toString() },
                    sampledRepeat.map { it.repoId.toString() },
                    "Stratified sampling should be deterministic"
                )
            }
        )
    }
}
