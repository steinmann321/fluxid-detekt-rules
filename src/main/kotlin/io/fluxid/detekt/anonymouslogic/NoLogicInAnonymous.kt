package io.fluxid.detekt.anonymouslogic

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * NoLogicInAnonymous — bans testable logic inside anonymous objects and lambdas.
 *
 * Principle: behavior worth testing must have a name. Logic hidden in anonymous
 * constructs (listeners, collectors, callbacks) should be extracted into named
 * methods and the anonymous body reduced to pure delegation.
 */
class NoLogicInAnonymous(config: Config) : Rule(config) {

    override val issue: Issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.Style,
        description = "Behavioral logic inside anonymous objects/lambdas must be extracted " +
            "to a named method and delegated to from the anonymous body.",
        debt = Debt.TWENTY_MINS,
    )

    private val maxStatements: Int by config(defaultValue = 2)

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        super.visitObjectLiteralExpression(expression)

        val functions = expression.objectDeclaration.declarations.filterIsInstance<KtNamedFunction>()

        functions.forEach { function ->
            val body = function.bodyExpression ?: return@forEach

            if (containsLogic(body)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(function),
                        message = "Anonymous object contains branch / multi-statement logic in " +
                            "${function.name ?: "anonymous function"}. Extract it to a " +
                            "named function and delegate from the anonymous body.",
                    ),
                )
            }
        }
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression) {
        super.visitLambdaExpression(expression)

        val body = expression.bodyExpression ?: return

        if (containsLogic(body)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "Lambda contains branch / multi-statement logic. " +
                        "Extract to a named function and delegate from the lambda.",
                ),
            )
        }
    }

    private fun containsLogic(block: KtBlockExpression): Boolean {
        val statements = block.statements

        // Pure delegation: a single call expression with no internal branching.
        if (statements.size == 1 && statements.first() is KtCallExpression) {
            return false
        }

        if (statements.size > maxStatements) return true

        return statements.any { stmt ->
            stmt is KtIfExpression ||
                stmt is KtWhenExpression ||
                stmt is KtTryExpression
        }
    }

    private fun containsLogic(body: KtExpression): Boolean {
        if (body is KtBlockExpression) {
            return containsLogic(body)
        }

        // Single-expression lambda. Allow a single call expression; flag when-expression
        // and if-expression bodies as logic.
        return when (body) {
            is KtCallExpression -> false
            is KtIfExpression -> true
            is KtWhenExpression -> true
            is KtTryExpression -> true
            else -> false
        }
    }
}
