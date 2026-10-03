package com.pekochan069.guitarlearner.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class ArchitectureIssueRegistry : IssueRegistry() {
    override val api: Int = CURRENT_API
    override val minApi: Int = CURRENT_API
    override val issues: List<Issue> = listOf(ArchitectureApiDetector.ISSUE, UnhandledEitherDetector.ISSUE)
}
