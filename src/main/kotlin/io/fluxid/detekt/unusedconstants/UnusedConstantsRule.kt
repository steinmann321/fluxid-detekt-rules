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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Flags constants in *Constants.kt-style files that are never referenced
 * anywhere under the configured [sourceRoots].
 *
 * Pure filesystem-based design — no PSI global state, no legacy keys.
 *
 * Example config:
 *
 * unused-constants:
 *   active: true
 *
 *   UnusedConstantsRule:
 *     active: true
 *     sourceRoots:
 *       - "src/main/java"
 *       - "src/main/kotlin"
 *       - "src/test/java"
 *       - "src/test/kotlin"
 *     filePatterns:
 *       - ".*Constants\\.kt"
 *     allowlist:
 *       - "SOME_INTENTIONALLY_UNUSED_CONSTANT"
 */
class UnusedConstantsRule(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = javaClass.simpleName,
        severity = Severity.CodeSmell,
        description = "Const values declared in constants files must be referenced at least once.",
        debt = Debt.FIVE_MINS,
    )

    private val filePatterns: List<Regex> =
        valueOrDefault(KEY_FILE_PATTERNS, DEFAULT_FILE_PATTERNS).map { Regex(it) }

    private val sourceRoots: List<String> =
        valueOrDefault(KEY_SOURCE_ROOTS, DEFAULT_SOURCE_ROOTS)

    private val allowlist: Set<String> =
        valueOrDefault(KEY_ALLOWLIST, emptyList<String>()).toSet()

    // Lazily built, project-wide index of all Kotlin file contents under the
    // configured sourceRoots. Built once per Detekt run, then read-only.
    private val allContents: List<String> by lazy {
        ProjectSourceIndex.build(sourceRoots)
    }

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)

        // Only treat files whose *filename* matches one of the configured
        // constant-container patterns as defining constants to be checked.
        val fileName = file.name
        if (filePatterns.none { it.matches(fileName) }) return

        val properties = file.collectDescendantsOfType<KtProperty> { property ->
            property.hasModifier(KtTokens.CONST_KEYWORD) &&
                !property.name.isNullOrBlank()
        }

        if (properties.isEmpty()) return

        val contents = allContents

        properties.forEach { property ->
            val name = property.name ?: return@forEach
            if (name in allowlist) return@forEach

            // Count word-boundary matches of the constant name across all
            // files indexed under the configured sourceRoots.
            val pattern = Regex("\\b" + Regex.escape(name) + "\\b")
            val occurrences = contents.sumOf { content ->
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
        fun build(sourceRoots: List<String>): List<String> {
            val result = mutableListOf<String>()

            sourceRoots.forEach { root ->
                val rootPath = Paths.get(root)
                if (!Files.exists(rootPath)) return@forEach

                Files.walk(rootPath).use { stream ->
                    stream
                        .filter { path ->
                            Files.isRegularFile(path) && path.toString().endsWith(".kt")
                        }
                        .forEach { path ->
                            result += readFileSafely(path)
                        }
                }
            }

            return result
        }

        private fun readFileSafely(path: Path): String =
            try {
                String(Files.readAllBytes(path))
            } catch (_: Exception) {
                ""
            }
    }

    companion object {
        private const val KEY_FILE_PATTERNS = "filePatterns"
        private const val KEY_SOURCE_ROOTS = "sourceRoots"
        private const val KEY_ALLOWLIST = "allowlist"

        private val DEFAULT_FILE_PATTERNS = listOf(".*Constants\\.kt")
        private val DEFAULT_SOURCE_ROOTS = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/test/java",
            "src/test/kotlin",
        )
    }
}
