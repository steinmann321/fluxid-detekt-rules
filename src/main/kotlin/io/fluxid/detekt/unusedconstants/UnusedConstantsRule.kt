package io.fluxid.detekt.unusedconstants

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags constants in *Constants.kt files that are never referenced anywhere
 * in the module.
 *
 * Project-agnostic behaviour is controlled via detekt config:
 *
 * unused-constants:
 *   active: true
 *
 *   UnusedConstantsRule:
 *     active: true
 *     filePattern: ".*Constants\\.kt"
 *     allowlist: [ "SOME_CONSTANT_TO_IGNORE" ]
 */
class UnusedConstantsRule(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.CodeSmell,
        description = "Const values declared in constants files must be referenced at least once.",
        debt = Debt.FIVE_MINS,
    )

    private val filePattern: Regex = Regex(
        valueOrDefault(KEY_FILE_PATTERN, DEFAULT_FILE_PATTERN),
    )

    private val allowlist: Set<String> =
        valueOrDefault(KEY_ALLOWLIST, emptyList<String>()).toSet()

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        // Register the raw text for this file so we can count constant name
        // occurrences across the whole module without relying on filesystem
        // layout or working directory assumptions.
        ProjectSourceIndex.register(file)

        val fileName = file.name
        if (!filePattern.matches(fileName)) return

        val properties = file.collectDescendantsOfType<KtProperty> { property ->
            property.hasModifier(KtTokens.CONST_KEYWORD) &&
                !property.name.isNullOrBlank()
        }

        if (properties.isEmpty()) return

        val allContents = ProjectSourceIndex.contents

        properties.forEach { property ->
            val name = property.name ?: return@forEach
            if (name in allowlist) return@forEach

            // Count word-boundary matches of the constant name across all
            // files analysed in this Detekt run.
            val pattern = Regex("\\b" + Regex.escape(name) + "\\b")
            val occurrences = allContents.sumOf { content ->
                pattern.findAll(content).count()
            }

            // One occurrence is the declaration itself; anything more means it is used.
            if (occurrences <= 1) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(property),
                        message = "Constant $name appears to be unused.",
                    ),
                )
            }
        }
    }

    private object ProjectSourceIndex {
        private val texts: MutableList<String> = mutableListOf()

        val contents: List<String>
            get() = texts

        fun register(file: KtFile) {
            texts += file.text
        }
    }

    companion object {
        private const val KEY_FILE_PATTERN = "filePattern"
        private const val KEY_ALLOWLIST = "allowlist"
        private const val DEFAULT_FILE_PATTERN = ".*Constants\\.kt"
    }
}
