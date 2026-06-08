# fluxid-detekt-rules

Reusable Detekt rulesets for Kotlin/JVM projects (including Android). This
library currently provides two project-agnostic rulesets:

- `anonymous-logic` — bans testable logic inside anonymous objects and lambdas
- `unused-constants` — flags unused `const val` declarations in `*Constants.kt`

Both rulesets are packaged as a Detekt plugin and are intended to be plugged
into your existing Detekt Gradle tasks.

## Getting started

The artifact is published via [JitPack](https://jitpack.io):

```kotlin
// settings.gradle[.kts]
dependencyResolutionManagement {
    repositories {
        google()            // if Android
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    // Detekt plugin containing the custom rulesets
    detektPlugins("com.github.steinmann321:fluxid-detekt-rules:v0.1.1")
}
```

In your Detekt Gradle tasks you must ensure that the `pluginClasspath` comes
from `configurations.detektPlugins`:

```kotlin
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektAnonymousLogic") {
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt-anonymous-logic.yml"))

    // Critical: load this plugin via detektPlugins configuration
    pluginClasspath.setFrom(configurations.detektPlugins)

    setSource(files("src/main/java", "src/main/kotlin"))
    parallel = true
}
```

## Rulesets and configuration

### 1) anonymous-logic ruleset

**RuleSet id:** `anonymous-logic`

**Rule:** `NoLogicInAnonymous`

**Purpose:**

Behavior that is worth testing should live in a named function. This rule bans
branching or multi-statement logic inside anonymous objects and lambdas (for
example, listeners, callbacks, collectors). The body of an anonymous construct
should be reduced to simple delegation to a named, directly-testable member.

**Key idea:**

- Anonymous objects/lambdas are hard to test directly.
- Put logic in a named method (`handlePlayerError`, `onItemSelected`, …).
- Keep the anonymous body as a single call expression delegating to that method.

**Configuration example (YAML):**

```yaml
config:
  validation: true
  warningsAsErrors: false

build:
  maxIssues: 0
  excludeCorrectable: false

anonymous-logic:
  active: true

  NoLogicInAnonymous:
    active: true
    maxStatements: 2
```

- `maxStatements` — maximum number of statements allowed inside an anonymous
  body before it is considered "logic". A single pure call expression is
  considered delegation and is allowed.

### 2) unused-constants ruleset

**RuleSet id:** `unused-constants`

**Rule:** `UnusedConstantsRule`

**Purpose:**

Keep your `*Constants.kt` surface small and meaningful by failing the build
when `const val` declarations are never referenced anywhere in the module.

This rule:

- Finds `const val` properties in files whose name matches `filePattern`.
- Builds a simple index of all Kotlin files under `src/**.kt`.
- Counts word-boundary matches of each constant name across that index.
- Reports constants whose name is seen only once (the declaration itself).

**Configuration example (YAML):**

```yaml
config:
  validation: true
  warningsAsErrors: false

build:
  maxIssues: 0
  excludeCorrectable: false

unused-constants:
  active: true

  UnusedConstantsRule:
    active: true
    filePattern: ".*Constants\\.kt"
    allowlist: []
```

- `filePattern` — regex applied to the *filename* (e.g. `.*Constants\.kt`);
  only matching files are scanned for constants.
- `allowlist` — list of constant names that are intentionally allowed to be
  unused (e.g. placeholder values, documented examples). Use sparingly and
  document the rationale next to the declaration.

## Security and pinning

All dependencies of this project are explicitly version-pinned in
`build.gradle.kts` (no `+` or dynamic versions).

## Typical Gradle wiring

A focused Detekt task per ruleset keeps enforcement explicit and fast. Example
for both rulesets inside an Android module:

```kotlin
// Bans logic inside anonymous objects/lambdas
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektAnonymousLogic") {
    description = "Bans logic inside anonymous objects and lambdas (NoLogicInAnonymous)."
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt-anonymous-logic.yml"))
    pluginClasspath.setFrom(configurations.detektPlugins)

    setSource(files("src/main/java", "src/main/kotlin"))
    parallel = true
}

// Flags unused constants in *Constants.kt files
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektUnusedConstants") {
    description = "Detects unused constants in *Constants.kt files via custom Detekt rule."
    buildUponDefaultConfig = false
    config.setFrom(files("$rootDir/config/detekt-unused-constants.yml"))
    pluginClasspath.setFrom(configurations.detektPlugins)

    setSource(files(
        "src/main/java", "src/main/kotlin",
        "src/test/java", "src/test/kotlin",
    ))
    parallel = true
}
```

From there you can wire these tasks into CI, pre-commit hooks, or your normal
`check` lifecycle as needed.
