package core.model

/**
 * Enumeration of supported UI frameworks.  The scanner detects the framework for
 * each repository and assigns it to the extracted components.  Unknown
 * frameworks are labelled as [UNKNOWN].
 */
enum class UiFramework {
    REACT,
    ANGULAR,
    VUE,
    UNKNOWN
}
