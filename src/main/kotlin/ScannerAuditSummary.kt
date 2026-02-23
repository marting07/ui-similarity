package audit

data class ScannerAuditRow(
    val repoId: String,
    val framework: String,
    val simpleCount: Int,
    val astCount: Int,
    val onlySimple: Int,
    val onlyAst: Int,
    val onlySimpleSample: String,
    val onlyAstSample: String
) {
    val mismatchTotal: Int get() = onlySimple + onlyAst
}

data class FrameworkAuditSummary(
    val framework: String,
    val repos: Int,
    val mismatchRepos: Int,
    val simpleTotal: Int,
    val astTotal: Int,
    val onlySimpleTotal: Int,
    val onlyAstTotal: Int
)

data class ScannerAuditSummaryReport(
    val totalRepos: Int,
    val mismatchRepos: Int,
    val frameworks: List<FrameworkAuditSummary>,
    val topMismatches: List<ScannerAuditRow>
)

fun summarizeScannerAuditRows(rows: List<ScannerAuditRow>, topN: Int = 10): ScannerAuditSummaryReport {
    val grouped = rows.groupBy { it.framework }
    val frameworkSummaries = grouped.map { (framework, fwRows) ->
        FrameworkAuditSummary(
            framework = framework,
            repos = fwRows.size,
            mismatchRepos = fwRows.count { it.mismatchTotal > 0 },
            simpleTotal = fwRows.sumOf { it.simpleCount },
            astTotal = fwRows.sumOf { it.astCount },
            onlySimpleTotal = fwRows.sumOf { it.onlySimple },
            onlyAstTotal = fwRows.sumOf { it.onlyAst }
        )
    }.sortedBy { it.framework }

    val mismatchRows = rows.filter { it.mismatchTotal > 0 }
        .sortedByDescending { it.mismatchTotal }
        .take(topN)

    return ScannerAuditSummaryReport(
        totalRepos = rows.size,
        mismatchRepos = rows.count { it.mismatchTotal > 0 },
        frameworks = frameworkSummaries,
        topMismatches = mismatchRows
    )
}
