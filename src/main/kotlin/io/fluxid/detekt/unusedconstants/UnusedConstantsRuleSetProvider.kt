package io.fluxid.detekt.unusedconstants

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Rule set provider for the unused-constants ruleset.
 */
class UnusedConstantsRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "unused-constants"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf<io.gitlab.arturbosch.detekt.api.Rule>(UnusedConstantsRule(config)),
        )
}
