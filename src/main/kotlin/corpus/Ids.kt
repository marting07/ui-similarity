package corpus

/**
 * Identifies a remote repository using a host, owner and name.  For example
 * `RepoId("github.com", "mui", "material-ui")` refers to the repository
 * `github.com/mui/material-ui`.  This ID is used to namespace component keys
 * and to locate cloned repositories on disk.
 */
data class RepoId(
    val host: String,
    val owner: String,
    val name: String
) {
    override fun toString(): String = "$host/$owner/$name"
}

/**
 * A unique key for a component within a repository.  It consists of the
 * repository ID, the relative path of the file containing the component and
 * the name under which the component is exported (for React).  The `id`
 * property yields a canonical string representation that can be used as a
 * primary key in maps and indexes.
 */
data class ComponentKey(
    val repoId: RepoId,
    val relativePath: String,
    val exportName: String
) {
    val id: String get() = "${repoId}:${relativePath}#${exportName}"
}