package com.appgate.tv.sitebrain

data class VerificationResult(
    val success: Boolean,
    val confidenceDelta: Double,
    val evidence: List<String>
)

object OutcomeVerifier {
    fun expectationsFor(action: SiteEdge, before: PageSnapshot): List<ActionExpectation> = when (action.actionKind) {
        ActionKind.SEARCH -> listOf(
            ActionExpectation(PostconditionKind.PAGE_TYPE_IS, pageType = action.expectedPageType ?: PageType.RESULT_LIST),
            ActionExpectation(PostconditionKind.RESULTS_CHANGED)
        )
        ActionKind.APPLY_FILTER -> listOf(ActionExpectation(PostconditionKind.CONSTRAINT_APPLIED))
        ActionKind.SORT -> listOf(ActionExpectation(PostconditionKind.RESULTS_CHANGED))
        ActionKind.PAGINATE -> listOf(ActionExpectation(PostconditionKind.RESULTS_CHANGED))
        ActionKind.OPEN_DETAIL -> listOf(ActionExpectation(PostconditionKind.PAGE_TYPE_IS, pageType = PageType.DETAIL))
        ActionKind.EXPAND -> listOf(ActionExpectation(PostconditionKind.TEXT_EXPANDED))
        ActionKind.OPEN_CATEGORY -> listOf(ActionExpectation(PostconditionKind.PAGE_TYPE_IS, pageType = PageType.CATEGORY))
        ActionKind.NAVIGATE, ActionKind.OPEN_TAB, ActionKind.BACK ->
            listOf(ActionExpectation(PostconditionKind.PAGE_TYPE_IS, pageType = action.expectedPageType))
        else -> emptyList()
    }

    fun verify(before: PageSnapshot, action: SiteEdge, after: PageSnapshot): VerificationResult {
        val beforeState = SemanticStateBuilder.from(before)
        val afterState = SemanticStateBuilder.from(after)
        val evidence = mutableListOf<String>()
        val expectations = expectationsFor(action, before)
        val checks = expectations.map { expect -> verifyExpectation(expect, before, after, beforeState, afterState, evidence) }

        // The verifier is now the only reward source. A changed DOM/fingerprint alone never
        // counts as success; at least one typed semantic postcondition must verify.
        val success = checks.isNotEmpty() && checks.any { it }
        if (!success && beforeState.semanticHash != afterState.semanticHash) {
            evidence += "semantic state changed but no expected postcondition verified"
        }
        return VerificationResult(
            success = success,
            confidenceDelta = if (success) 0.15 else -0.10,
            evidence = evidence.distinct()
        )
    }

    private fun verifyExpectation(
        expect: ActionExpectation,
        before: PageSnapshot,
        after: PageSnapshot,
        s: SemanticPageState,
        s2: SemanticPageState,
        evidence: MutableList<String>
    ): Boolean = when (expect.kind) {
        PostconditionKind.RESULTS_CHANGED -> {
            val changed = s.resultItemKeys.isNotEmpty() && s2.resultItemKeys.isNotEmpty() && s.resultItemKeys != s2.resultItemKeys
            if (changed) evidence += "result item keys changed"
            changed
        }
        PostconditionKind.CONSTRAINT_APPLIED -> {
            val selectedChanged = selectedLabels(before) != selectedLabels(after)
            val constraintsChanged = s.activeConstraints != s2.activeConstraints && s2.activeConstraints.isNotEmpty()
            if (selectedChanged) evidence += "selected filter controls changed"
            if (constraintsChanged) evidence += "active constraints changed"
            selectedChanged || constraintsChanged
        }
        PostconditionKind.PAGE_TYPE_IS -> {
            val target = expect.pageType
            val ok = target != null && s2.pageType == target && (s.pageType != s2.pageType || s.routeSignature != s2.routeSignature)
            if (ok) evidence += "page type reached: ${target.name}"
            ok
        }
        PostconditionKind.DETAIL_MATCHES -> {
            val ok = s2.pageType == PageType.DETAIL && (s.pageType != PageType.DETAIL || s.routeSignature != s2.routeSignature)
            if (ok) evidence += "detail page reached"
            ok
        }
        PostconditionKind.URL_QUERY_HAS -> {
            val key = expect.key.orEmpty()
            val ok = key.isNotBlank() && runCatching { java.net.URI(after.url).query.orEmpty().contains("$key=") }.getOrDefault(false)
            if (ok) evidence += "URL query contains $key"
            ok
        }
        PostconditionKind.DIALOG_OPENED -> {
            val ok = s2.affordanceRoles.contains(ActionKind.BACK) && !s.affordanceRoles.contains(ActionKind.BACK)
            if (ok) evidence += "dialog-like navigation affordance appeared"
            ok
        }
        PostconditionKind.TEXT_EXPANDED -> {
            val ok = after.visibleTextSummary.length > before.visibleTextSummary.length + 40
            if (ok) evidence += "visible text expanded"
            ok
        }
        PostconditionKind.END_OF_RESULTS -> {
            val ok = s.resultItemKeys.isNotEmpty() && s.resultItemKeys == s2.resultItemKeys
            if (ok) evidence += "no new result item keys"
            ok
        }
    }

    private fun selectedLabels(snapshot: PageSnapshot): Set<String> = snapshot.elements
        .filter { it.selected }
        .map { it.label.lowercase().trim() }
        .filter { it.isNotBlank() }
        .toSet()
}
