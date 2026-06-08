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

        val fileName = file.name
        if (!filePattern.matches(fileName)) return

        val properties = file.collectDescendantsOfType<KtProperty> { property ->
            property.hasModifier(KtTokens.CONST_KEYWORD) &&
                !property.name.isNullOrBlank()
        }

        if (properties.isEmpty()) return

        val contentsByPath = ProjectSourceIndex.contents

        properties.forEach { property ->
            val name = property.name ?: return@forEach
            if (name in allowlist) return@forEach

            // Count word-boundary matches of the constant name across the module.
            val pattern = Regex("\\b" + Regex.escape(name) + "\\b")
            val occurrences = contentsByPath.values.sumOf { content ->
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
        val contents: Map<Path, String> by lazy { loadContents() }

        private fun loadContents(): Map<Path, String> {
            return try {
                // Assume the working directory is the module root and scan src/**.kt.
                val srcRoot = Paths.get("src")
                if (!Files.exists(srcRoot)) return emptyMap()

                Files.walk(srcRoot).use { stream ->
                    val result = mutableMapOf<Path, String>()
                    stream
                        .filter { path ->
                            Files.isRegularFile(path) && path.toString().endsWith(".kt")
                        }
                        .forEach { path ->
                            val content = try {
                                String(Files.readAllBytes(path))
                            } catch (_: Exception) {
                                ""
                            }
                            result[path] = content
                        }
                    result
                }
            } catch (e: Exception) {
                throw IllegalStateException("UnusedConstants index FAILED: ${'$'}{e.message}", e)
            }
        }
    }

    companion object {
        private const val KEY_FILE_PATTERN = "filePattern"
        private const val KEY_ALLOWLIST = "allowlist"
        private const val DEFAULT_FILE_PATTERN = ".*Constants\\.kt"
    }
}
