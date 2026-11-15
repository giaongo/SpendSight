import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// OPS-10 · Deployed service address, set at build time.
// First match wins: -Pspendsight.apiUrl=… / gradle.properties, the SPENDSIGHT_API_URL
// environment variable, then `spendsight.apiUrl=` in frontend/local.properties (gitignored;
// the easy way for Xcode builds). Unset means the local dev address on each platform.
val spendsightApiUrl: String = run {
    val local = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }
    (
        providers.gradleProperty("spendsight.apiUrl").orNull
            ?: providers.environmentVariable("SPENDSIGHT_API_URL").orNull
            ?: local.getProperty("spendsight.apiUrl")
            ?: ""
    ).trim().trimEnd('/')
}
require(spendsightApiUrl.isEmpty() || Regex("""^https://[A-Za-z0-9.\-]+(:[0-9]{1,5})?$""").matches(spendsightApiUrl)) {
    "spendsight.apiUrl must look like https://host[:port] with no path, got '$spendsightApiUrl'"
}

val generateStringTable by tasks.registering {
    val enFile = layout.projectDirectory.file("src/commonMain/translations/values/strings.xml")
    val fiFile = layout.projectDirectory.file("src/commonMain/translations/values-fi-rFI/strings.xml")
    val svFile = layout.projectDirectory.file("src/commonMain/translations/values-sv-rFI/strings.xml")
    val outDir = layout.buildDirectory.dir("generated/spendsight-strings/kotlin")
    inputs.file(enFile)
    inputs.file(fiFile)
    inputs.file(svFile)
    outputs.dir(outDir)
    doLast {
        fun androidUnescape(raw: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < raw.length) {
                if (raw[i] == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        '\'' -> { out.append('\''); i += 2; continue }
                        '"' -> { out.append('"'); i += 2; continue }
                        '\\' -> { out.append('\\'); i += 2; continue }
                        'n' -> { out.append('\n'); i += 2; continue }
                        't' -> { out.append('\t'); i += 2; continue }
                    }
                }
                if (raw[i] == '%' && i + 1 < raw.length && raw[i + 1] == '%') {
                    out.append('%')
                    i += 2
                    continue
                }
                out.append(raw[i])
                i++
            }
            return out.toString()
        }
        fun kotlinString(value: String): String = buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> { append('\\'); append('\\') }
                    '"' -> { append('\\'); append('"') }
                    '$' -> { append('\\'); append('$') }
                    '\n' -> { append('\\'); append('n') }
                    '\r' -> { append('\\'); append('r') }
                    else -> append(ch)
                }
            }
            append('"')
        }
        fun readStrings(file: java.io.File): Map<String, String> {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            val doc = factory.newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val out = linkedMapOf<String, String>()
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as org.w3c.dom.Element
                val name = node.getAttribute("name")
                require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Bad string name '$name'" }
                out[name] = androidUnescape(node.textContent ?: "")
            }
            return out
        }
        val en = readStrings(enFile.asFile)
        val fi = readStrings(fiFile.asFile)
        val sv = readStrings(svFile.asFile)
        fun requireSameKeys(label: String, other: Map<String, String>) {
            val missing = en.keys - other.keys
            val extra = other.keys - en.keys
            require(extra.isEmpty()) { "Translation keys differ. Extra in $label: $extra." }
            // English-only for now: a key missing here shows the English text (see StringTable.get).
            if (missing.isNotEmpty()) logger.warn("Not yet translated to $label, shown in English: $missing")
        }
        requireSameKeys("Finnish", fi)
        requireSameKeys("Swedish", sv)
        val keys = en.keys.sorted()
        val file = outDir.get().file("fi/spendsight/StringTable.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("// Generated from translations/values, values-fi-rFI, and values-sv-rFI. Do not edit.")
                appendLine("package fi.spendsight")
                appendLine()
                appendLine("internal object StringTable {")
                appendLine("    fun get(lang: Lang, name: String): String {")
                appendLine("        val table = when (lang) {")
                appendLine("            Lang.Fi -> fi")
                appendLine("            Lang.Sv -> sv")
                appendLine("            Lang.En -> en")
                appendLine("        }")
                appendLine("        return table[name] ?: en[name] ?: name")
                appendLine("    }")
                appendLine()
                appendLine("    private val en: Map<String, String> = buildMap {")
                for (key in keys) appendLine("        put(${kotlinString(key)}, ${kotlinString(en.getValue(key))})")
                appendLine("    }")
                appendLine()
                appendLine("    private val fi: Map<String, String> = buildMap {")
                for (key in keys) fi[key]?.let { appendLine("        put(${kotlinString(key)}, ${kotlinString(it)})") }
                appendLine("    }")
                appendLine()
                appendLine("    private val sv: Map<String, String> = buildMap {")
                for (key in keys) sv[key]?.let { appendLine("        put(${kotlinString(key)}, ${kotlinString(it)})") }
                appendLine("    }")
                appendLine("}")
                appendLine()
                appendLine("/** String names. Same role as R.string in the balance app. */")
                appendLine("object R {")
                appendLine("    object string {")
                for (key in keys) appendLine("        const val $key = ${kotlinString(key)}")
                appendLine("    }")
                appendLine("}")
                appendLine()
            },
            Charsets.UTF_8,
        )
    }
}

val generateBuildSettings by tasks.registering {
    val url = spendsightApiUrl
    val outDir = layout.buildDirectory.dir("generated/spendsight/kotlin")
    inputs.property("apiUrl", url)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("fi/spendsight/BuildSettings.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated by shared/build.gradle.kts (OPS-10). Do not edit.
            |package fi.spendsight
            |
            |/** The `spendsight.apiUrl` build setting. Empty means the local dev address. */
            |internal const val DEPLOYED_BASE_URL: String = "$url"
            |""".trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildSettings)
            kotlin.srcDir(generateStringTable)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.ktor.core)
            implementation(libs.ktor.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            implementation(libs.ktor.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
}

android {
    namespace = "fi.spendsight.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
