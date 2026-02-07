package extractor.simple

import core.model.BehaviorFeatures
import extractor.BehaviorFeatureExtractor
import extractor.ComponentSource

/**
 * A naive behaviour extractor that derives simple features from the logic
 * source code string.  It looks for event handler declarations, state
 * management usage, API calls and basic complexity indicators.  This
 * implementation is intentionally lightweight and should be replaced with
 * proper parsing (e.g. using the TypeScript compiler API) for real
 * experiments.
 */
class SimpleBehaviorFeatureExtractor : BehaviorFeatureExtractor {
    override fun extractBehaviorFeatures(source: ComponentSource): BehaviorFeatures {
        val code = source.logicCode
        val eventTypes = mutableSetOf<String>()
        // Event handler detection
        if (code.contains("onClick", ignoreCase = true) || code.contains("(click)", ignoreCase = true)) {
            eventTypes += "click"
        }
        if (code.contains("onChange", ignoreCase = true) || code.contains("(change)", ignoreCase = true)) {
            eventTypes += "change"
        }
        if (code.contains("onSubmit", ignoreCase = true) || code.contains("(submit)", ignoreCase = true)) {
            eventTypes += "submit"
        }
        if (code.contains("onKeyDown", ignoreCase = true) || code.contains("(keydown)", ignoreCase = true)) {
            eventTypes += "keydown"
        }
        // Interaction patterns: naive heuristics
        val interactionPatterns = mutableSetOf<String>()
        if (code.contains("useState", ignoreCase = true) && code.contains("toggle", ignoreCase = true)) {
            interactionPatterns += "click-toggle"
        }
        if (code.contains("filter(", ignoreCase = true)) {
            interactionPatterns += "list-filter"
        }
        if (code.contains("form", ignoreCase = true)) {
            interactionPatterns += "form"
        }
        // State management: detect useState/useReducer/NgRx/Vuex
        val statePatterns = mutableSetOf<String>()
        if (code.contains("useState(", ignoreCase = true) || code.contains("useReducer(", ignoreCase = true)) {
            statePatterns += "localState"
        }
        if (code.contains("NgRx", ignoreCase = true)) statePatterns += "ngrx"
        if (code.contains("Vuex", ignoreCase = true)) statePatterns += "vuex"
        // API calls: detect fetch/axios calls and imported services
        val apiSignatures = mutableSetOf<String>()
        val fetchRegex = Regex("fetch([A-Za-z0-9_]*)", RegexOption.IGNORE_CASE)
        for (match in fetchRegex.findAll(code)) {
            apiSignatures += "fetch${match.groupValues[1]}"
        }
        val axiosRegex = Regex("axios\\.([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
        for (match in axiosRegex.findAll(code)) {
            apiSignatures += "axios.${match.groupValues[1]}"
        }
        // Complexity metrics: count branching constructs and handler definitions
        fun tokenCount(text: String, token: String): Int = token.toRegex(RegexOption.IGNORE_CASE).findAll(text).count()
        val cyclomatic = 1 + tokenCount(code, "if ") + tokenCount(code, "else if") + tokenCount(code, "for ") + tokenCount(code, "while ") + tokenCount(code, "switch ")
        val handlerCount = eventTypes.size
        val apiCallCount = apiSignatures.size
        val conditionalCount = tokenCount(code, "if ") + tokenCount(code, "switch ")
        return BehaviorFeatures(
            eventTypes = eventTypes,
            interactionPatterns = interactionPatterns,
            statePatterns = statePatterns,
            apiSignatures = apiSignatures,
            cyclomatic = cyclomatic,
            handlerCount = handlerCount,
            apiCallCount = apiCallCount,
            conditionalCount = conditionalCount
        )
    }
}
