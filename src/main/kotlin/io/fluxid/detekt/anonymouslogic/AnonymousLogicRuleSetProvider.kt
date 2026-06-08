package io.fluxid.detekt.anonymouslogic

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Provides the custom rule set containing [NoLogicInAnonymous].
 */
class AnonymousLogicRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "anonymous-logic"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(NoLogicInAnonymous(config)),
        )
}
