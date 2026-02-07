package core.similarity

import core.model.BehaviorFeatures
import core.model.ComponentSignature
import core.model.CssFeatures
import core.model.DomFeatures

/**
 * A compound distance combining DOM, CSS and behaviour distances.  The
 * weights [alpha], [beta] and [gamma] must sum to 1.0 to produce a normalised
 * distance in [0,1].  Each sub‑distance is itself assumed to be normalised.
 */
class ComponentDistance(
    private val domDistance: Distance<DomFeatures> = DomDistance(),
    private val cssDistance: Distance<CssFeatures> = CssDistance(),
    private val behaviorDistance: Distance<BehaviorFeatures> = BehaviorDistance(),
    private val alpha: Double = 0.4,
    private val beta: Double = 0.3,
    private val gamma: Double = 0.3
) : Distance<ComponentSignature> {
    override fun distance(a: ComponentSignature, b: ComponentSignature): Double {
        val dDom = domDistance.distance(a.dom, b.dom)
        val dCss = cssDistance.distance(a.css, b.css)
        val dBeh = behaviorDistance.distance(a.behavior, b.behavior)
        return alpha * dDom + beta * dCss + gamma * dBeh
    }
}
