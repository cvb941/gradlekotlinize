object GradleKotlinConverter {

    fun convert(groovyCode: String): String {
        return groovyCode
            .replaceApostrophes()
            .replaceDefWithVal()
            .convertMapExpression() // Run before array
            .convertFileTree()
            .convertArrayExpression()
            .convertManifestPlaceHoldersWithMap() // Run after convertMapExpression
            .convertVariableDeclaration()
            .convertPlugins()
            .convertPluginsIntoOneBlock()
            .convertPluginsFrom()
            .convertVariantFilter()
            .convertAndroidBuildConfigFunctions()
            .convertCompileToImplementation()
            .convertDependencies()
            .convertMaven()
            .addParentheses()
            .addEquals()
            .convertJavaCompatibility()
            .convertCleanTask()
            .convertProguardFiles()
            .convertInternalBlocks()
            .convertInclude()
            .convertBuildTypes()
            .convertProductFlavors()
            .convertSourceSets()
            .convertSigningConfigs()
            .convertExcludeClasspath()
            .convertExcludeModules()
            .convertExcludeGroups()
            .convertJetBrainsKotlin()
            .convertSigningConfigBuildType()
            .convertExtToExtra()
            .addParenthesisToId()
            .replaceColonWithEquals()
    }

    // anything with ' ('1.0.0', 'kotlin-android', 'jitpack', etc)
// becomes
// anything with " ("1.0.0", "kotlin-android", "jitpack", etc)
// We do not replace '"45"' --> "\"45\"" becaues we cannot safely match start and end quote with regExp's
    fun String.replaceApostrophes(): String = this.replace("'", "\"")

    // def appcompat = "1.0.0"
// becomes
// val appcompat = "1.0.0"
    fun String.replaceDefWithVal(): String = this.replace("(^|\\s)def ".toRegex()) { valReplacer ->
        // only convert when " def " or "def " (start of the line).
        // if a variable is named highdef, it won't be converted.
        valReplacer.value.replace("def", "val")
    }


    fun String.convertType(): String =
        when (this) {
            "byte" -> "Byte"
            "short" -> "Short"
            "int" -> "Int"
            "long" -> "Long"
            "float" -> "Float"
            "double" -> "Double"
            "char" -> "Char"
            "boolean" -> "Boolean"
            else -> this
        }

    // final String<T> foo = "bar"
// becomes
// val foo: String<T> = "bar"
    fun String.convertVariableDeclaration(): String {
        val varDeclExp = """(?:final\s+)?(\w+)(<.+>)? +(\w+)\s*=\s*(.+)""".toRegex()

        return this.replace(varDeclExp) {
            val (type, genericsType, id, value) = it.destructured
            if (type == "val") {
                it.value
            } else {
                "val $id: ${type.convertType()}${genericsType.orEmpty()} = $value"
            }
        }
    }

    // [appIcon: "@drawable/ic_launcher", appRoundIcon: "@null"]
// becomes
// mapOf(appIcon to "@drawable/ic_launcher", appRoundIcon to "@null"])
    fun String.convertMapExpression(): String {
        val key = """\w+"""
        val value = """[^,:\s\]]+"""
        val keyValueGroup = """\s*$key:\s*$value\s*"""
        val mapRegExp = """\[($keyValueGroup(?:,$keyValueGroup)*)\]""".toRegex()
        val extractOneGroupRegExp = """^\s*($key):\s*($value)\s*(?:,(.*)|)$""".toRegex() // Matches key, value, the-rest after comma if any

        fun extractAllMatches(matchesInKotlinCode: MutableList<String>, remainingString: String) { // Extract the first key=value, and recurse on the postfix
            val innerMatch: MatchResult = extractOneGroupRegExp.find(remainingString) ?: return
            val innerGroups = innerMatch.groupValues
            matchesInKotlinCode += """"${innerGroups[1]}" to ${innerGroups[2]}"""
            if (innerGroups[3].isNotEmpty()) {
                val withoutComma = innerGroups[3]//.substring(1)
                extractAllMatches(matchesInKotlinCode, withoutComma)
            }
        }

        return this.replace(mapRegExp) { lineMatch ->
            val matchesInKotlinCode = mutableListOf<String>()
            extractAllMatches(matchesInKotlinCode, lineMatch.groupValues[1])
            "mapOf(${matchesInKotlinCode.joinToString(", ")})"
        }
    }

    // Use new com.android.tools.build:gradle:4.1.0 syntax for manifestPlaceholders
// manifestPlaceholders = mapOf("appIcon" to "@drawable/ic_launcher")
// becomes
// manifestPlaceholders.putAll(mapOf("appIcon" to "@drawable/ic_launcher"))
    fun String.convertManifestPlaceHoldersWithMap(): String {
        val regExp = """manifestPlaceholders = (mapOf\([^\)]*\))""".toRegex()
        return this.replace(regExp) {
            "manifestPlaceholders.putAll(${it.groupValues[1]})"
        }
    }

    // [1, 2]
// becomes
// listOf(1,2)
// but keep probablyMyArrayLookup[42]
    fun String.convertArrayExpression(): String {
        val arrayExp = """\[([^\]]*?)\]""".toRegex()

        return this.replace(arrayExp) {
            if (it.groupValues[1].toIntOrNull() != null) {
                it.value // Its probably an array indexing, so keep original
            } else {
                "listOf(${it.groupValues[1]})"
            }
        }
    }

    fun String.convertVariantFilter(): String {
        val arrayExp = """variantFilter\s*\{\s*(\w+\s*->)""".toRegex()

        return this.replace(arrayExp) {
            "variantFilter { // ${it.groupValues[1]} - TODO Manually replace '${it.groupValues[1]}' variable with this, and setIgnore(true) with ignore = true\n"
        }
    }


    // apply plugin: "kotlin-android"
// becomes
// apply(plugin = "kotlin-android")
    fun String.convertPlugins(): String {
        val pluginsExp = """apply plugin: (\S+)""".toRegex()

        return this.replace(pluginsExp) {
            val (pluginId) = it.destructured
            // it identifies the plugin id and rebuilds the line.
            "apply(plugin = $pluginId)"
        }
    }

    // apply from: "kotlin-android"
// becomes
// apply(from = "kotlin-android")
    fun String.convertPluginsFrom(): String {
        val pluginsExp = """apply from: (\S+)""".toRegex()

        return this.replace(pluginsExp) {
            val (pluginId) = it.destructured
            "apply(from = $pluginId)"
        }
    }

    fun String.convertAndroidBuildConfigFunctions(): String {
        val outerExp = """(buildConfigField|resValue|flavorDimensions|exclude|java.srcDir)\s+(".*")""".toRegex()
        // packagingOptions > exclude
        // sourceSets > name("") > java.srcDir

        return this.replace(outerExp) {
            val groups = it.groupValues
            "${groups[1]}(${groups[2]})"
        }
    }


    // NEED TO RUN BEFORE [convertDependencies].
// compile ":epoxy-annotations"
// becomes
// implementation ":epoxy-annotations"
    fun String.convertCompileToImplementation(): String {
        val outerExp = "(compile|testCompile)(?!O).*\".*\"".toRegex()

        return this.replace(outerExp) {
            if ("testCompile" in it.value) {
                it.value.replace("testCompile", "testImplementation")
            } else {
                it.value.replace("compile", "implementation")
            }
        }
    }


    // implementation ":epoxy-annotations"
// becomes
// implementation(":epoxy-annotations")
    fun String.convertDependencies(): String {

        val testKeywords = "testImplementation|androidTestImplementation|debugImplementation|compileOnly|testCompileOnly|runtimeOnly|developmentOnly"
        val gradleKeywords = "($testKeywords|implementation|api|annotationProcessor|classpath|kaptTest|kaptAndroidTest|kapt|check)".toRegex()

        // ignore cases like kapt { correctErrorTypes = true } and apply plugin: ('kotlin-kapt") but pass kapt("...")
        // ignore keyWord followed by a space and a { or a " and a )
        val validKeywords = "(?!$gradleKeywords\\s*(\\{|\"\\)|\\.))$gradleKeywords.*".toRegex()

        return this.replace(validKeywords) { substring ->
            // By pass sth like: implementation(":epoxy-annotations") { ... }
            if (substring.value.contains("""\)(\s*)\{""".toRegex())) return@replace substring.value

            // retrieve the comment [//this is a comment], if any
            val comment = "\\s*\\/\\/.*".toRegex().find(substring.value)?.value ?: ""

            // remove the comment from the string. It will be added again at the end.
            val processedSubstring = substring.value.replace(comment, "")

            // we want to know if it is a implementation, api, etc
            val gradleKeyword = gradleKeywords.find(processedSubstring)?.value

            // implementation ':epoxy-annotations' becomes 'epoxy-annotations'
            val isolated = processedSubstring.replaceFirst(gradleKeywords, "").trim()

            // can't be && for the kapt project(':epoxy-processor') scenario, where there is a ) on the last element.
            if (isolated != "" && (isolated.first() != '(' || isolated.last { it != ' ' } != ')')) {
                "$gradleKeyword($isolated)$comment"
            } else {
                "$gradleKeyword$isolated$comment"
            }
        }
    }

    // fileTree(dir: "libs", include: ["*.jar"])
// becomes
// fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))
    fun String.convertFileTree(): String {
        val fileTreeString = """fileTree\(dir(\s*):(\s*)"libs"(\s*),(\s*)include(\s*):(\s*)\["\*.jar"\]\)""".toRegex()

        return this.replace(fileTreeString, """fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar")))""")
    }


    // signingConfig signingConfigs.release
// becomes
// signingConfig = signingConfigs.getByName("release")
    fun String.convertSigningConfigBuildType(): String {
        val outerExp = "signingConfig.*signingConfigs.*".toRegex()

        return this.replace(outerExp) {
            // extracts release from signingConfig signingConfigs.release
            val release = it.value.replace("signingConfig.*signingConfigs.".toRegex(), "")
            "signingConfig = signingConfigs.getByName(\"$release\")"
        }
    }


    // buildTypes { release }
// becomes
// buildTypes { named("release") }
    fun String.convertBuildTypes(): String = this.convertNestedTypes("buildTypes", "named")

    // productFlavors { myName }
// becomes
// productFlavors { create("myName") }
    fun String.convertProductFlavors(): String = this.convertNestedTypes("productFlavors", "create")


    // sourceSets { test }
// becomes
// sourceSets { named("test") }
    fun String.convertSourceSets(): String = this.convertNestedTypes("sourceSets", "named")


    // signingConfigs { release }
// becomes
// signingConfigs { register("release") }
    fun String.convertSigningConfigs(): String = this.convertNestedTypes("signingConfigs", "register")


    fun String.convertNestedTypes(buildTypes: String, named: String): String {
        return this.getExpressionBlock("$buildTypes\\s*\\{".toRegex()) { substring ->
            substring.replace("\\S*\\s(?=\\{)".toRegex()) {
                val valueWithoutWhitespace = it.value.replace(" ", "")
                "$named(\"$valueWithoutWhitespace\") "
            }
        }
    }


    fun String.getExpressionBlock(
        expression: Regex,
        modifyResult: ((String) -> (String))
    ): String {

        val stringSize = this.count()

        return expression.findAll(this)
            .toList()
            .foldRight(this) { matchResult, accString ->

                var rangeStart = matchResult.range.last
                var rangeEnd = stringSize
                var count = 0

                for (item in rangeStart..stringSize) {
                    if (this[item] == '{') count += 1 else if (this[item] == '}') count -= 1
                    if (count == 0) {
                        rangeEnd = item
                        break
                    }
                }

                val convertedStr = modifyResult.invoke(this.substring(rangeStart, rangeEnd))

                accString.replaceRange(rangeStart, rangeEnd, convertedStr)
            }
    }


    // maven { url "https://maven.fabric.io/public" }
// becomes
// maven("https://maven.fabric.io/public")
    fun String.convertMaven(): String {

        val mavenExp = "maven\\s*\\{\\s*url\\s*(.*?)\\s*?\\}".toRegex()

        return this.replace(mavenExp) {
            it.value.replace("(= *uri *\\()|\\)|(url)|( )".toRegex(), "")
                .replace("{", "(")
                .replace("}", ")")
        }
    }

    var showWarningGroovyVariables = false

    // compileSdkVersion 28
// becomes
// compileSdkVersion(28)
    fun String.addParentheses(): String {

        val sdkExp = "(compileSdkVersion|minSdkVersion|targetSdkVersion)\\s*([^\\s]*)(.*)".toRegex() // include any word, as it may be a variable

        return this.replace(sdkExp) {
            val groups = it.groupValues
            if (groups.size > 3) {
                if (groups[2].toIntOrNull() == null) showWarningGroovyVariables = true
                "${groups[1]}(${groups[2]})${groups[3]}" // group 3 for preserving comments
            } else {
                it.value
            }
        }
    }


    // id "io.gitlab.arturbosch.detekt" version "1.0.0.RC8"
// becomes
// id("io.gitlab.arturbosch.detekt") version "1.0.0.RC8"
    fun String.addParenthesisToId(): String {

        // this will only catch id "..." version ..., should skip id("...")
        // should get the id "..."
        val idExp = "id\\s*\".*?\"".toRegex()

        return this.replace(idExp) {
            // remove the "id " before the real id
            val idValue = it.value.replace("id\\s*".toRegex(), "")
            "id($idValue)"
        }
    }


    // versionCode 4
// becomes
// versionCode = 4
    fun String.addEquals(): String {

        val signing = "keyAlias|keyPassword|storeFile|storePassword"
        val other = "multiDexEnabled|correctErrorTypes|javaMaxHeapSize|jumboMode|dimension|useSupportLibrary"
        val databinding = "dataBinding|viewBinding"
        val defaultConfig = "applicationId|versionCode|versionName|testInstrumentationRunner"
        val negativeLookAhead = "(?!\\{)[^\\s]" // Don't want '{' as next word character

        val versionExp = """($defaultConfig|$signing|$other|$databinding)\s*${negativeLookAhead}.*""".toRegex()

        return this.replace(versionExp) {
            val split = it.value.split(" ")

            // if there is more than one whitespace, the last().toIntOrNull() will find.
            if (split.lastOrNull { it.isNotBlank() } != null) {
                "${split[0]} = ${split.last()}"
            } else {
                it.value
            }
        }
    }


    // proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
// becomes
// setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    fun String.convertProguardFiles(): String {

        val proguardExp = "proguardFiles .*".toRegex()

        return this.replace(proguardExp) {
            val isolatedArgs = it.value.replace("proguardFiles\\s*".toRegex(), "")
            "setProguardFiles(listOf($isolatedArgs))"
        }
    }


    // ext.enableCrashlytics = false
// becomes
// extra.set("enableCrashlytics", false)
    fun String.convertExtToExtra(): String {

        // get ext... but not ext { ... }
        val outerExp = """ext\.(\w+)\s*=\s*(.*)""".toRegex()

        return this.replace(outerExp) {
            val (name, value) = it.destructured

            "extra[\"$name\"] = $value"
        }
    }


    // sourceCompatibility = "1.8" or sourceCompatibility JavaVersion.VERSION_1_8
// becomes
// sourceCompatibility = JavaVersion.VERSION_1_8
    fun String.convertJavaCompatibility(): String {

        val compatibilityExp = "(sourceCompatibility|targetCompatibility).*".toRegex()

        return this.replace(compatibilityExp) {
            val split = it.value.replace("\"]*".toRegex(), "").split(" ")

            if (split.lastOrNull() != null) {
                if ("JavaVersion" in split.last()) {
                    "${split[0]} = ${split.last()}"
                } else {
                    "${split[0]} = JavaVersion.VERSION_${split.last().replace(".", "_")}"
                }
            } else {
                it.value
            }
        }
    }


    // converts the clean task, which is very common to find
    fun String.convertCleanTask(): String {

        val cleanExp = "task clean\\(type: Delete\\)\\s*\\{[\\s\\S]*\\}".toRegex()
        val registerClean = "tasks.register<Delete>(\"clean\").configure {\n" +
                "    delete(rootProject.buildDir)\n }"

        return this.replace(cleanExp, registerClean)
    }


    // androidExtensions { experimental = true }
// becomes
// androidExtensions { isExperimental = true }
    fun String.convertInternalBlocks(): String {
        return this.addIsToStr("androidExtensions", "experimental")
            .addIsToStr("dataBinding", "enabled")
            .addIsToStr("lintOptions", "abortOnError")
            .addIsToStr("buildTypes", "debuggable")
            .addIsToStr("buildTypes", "minifyEnabled")
            .addIsToStr("buildTypes", "shrinkResources")
            .addIsToStr("", "transitive")
    }

    fun String.addIsToStr(blockTitle: String, transform: String): String {

        val extensionsExp = "$blockTitle\\s*\\{[\\s\\S]*\\}".toRegex()

        if (!extensionsExp.containsMatchIn(this)) return this

        val typesExp = "$transform.*".toRegex()

        return this.replace(typesExp) {

            val split = it.value.split(" ")

            // if there is more than one whitespace, the last().toIntOrNull() will find.
            if (split.lastOrNull { it.isNotBlank() } != null) {
                "is${split[0].capitalize()} = ${split.last()}"
            } else {
                it.value
            }
        }
    }


    // include ":app", ":diffutils"
// becomes
// include(":app", ":diffutils")
    fun String.convertInclude(): String {

        val expressionBase = "\\s*((\".*\"\\s*,)\\s*)*(\".*\")".toRegex()
        val includeExp = "include$expressionBase".toRegex()

        return this.replace(includeExp) { includeBlock ->
            if(includeBlock.value.contains("include\"")) return@replace includeBlock.value // exclude: "include" to

            // avoid cases where some lines at the start/end are blank
            val multiLine = includeBlock.value.split('\n').count { it.isNotBlank() } > 1

            val isolated = expressionBase.find(includeBlock.value)?.value ?: ""
            if (multiLine) "include(\n${isolated.trim()}\n)" else "include(${isolated.trim()})"
            // Possible visual improvement: when using multiline, the first line should have the same
            // margin/spacement as the others.
        }
    }


    // configurations.classpath.exclude group: 'com.android.tools.external.lombok'
// becomes
// configurations.classpath {
//    exclude(group = "com.android.tools.external.lombok")
// }
    fun String.convertExcludeClasspath(): String {

        val fullLineExp = ".*configurations\\.classpath\\.exclude.*group:.*".toRegex()

        // this will extract "com.android.tools.external.lombok" from the string.
        val innerExp = "\".*\"".toRegex()

        return this.replace(fullLineExp) { isolatedLine ->
            val isolatedStr = innerExp.find(isolatedLine.value)?.value ?: ""
            "configurations.classpath {\n" +
                    "    exclude(group = $isolatedStr)\n" +
                    "}"
        }
    }

    // exclude module: 'module-id'
// becomes
// exclude(module = "module-id")
    fun String.convertExcludeModules(): String {
        val fullLineExp = """exclude module: (\S+)""".toRegex()

        return this.replace(fullLineExp) {
            val (moduleId) = it.destructured
            "exclude(module = $moduleId)"
        }
    }

    // exclude group: 'group-id'
// becomes
// exclude(group = "group-id")
    fun String.convertExcludeGroups(): String {
        val fullLineExp = """exclude group: (\S+)""".toRegex()

        return this.replace(fullLineExp) {
            val (groupId) = it.destructured
            "exclude(group = $groupId)"
        }
    }

    // classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
// becomes
// classpath(kotlin("gradle-plugin", version = "$kotlin_version"))
//
// implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
// becomes
// implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))
    fun String.convertJetBrainsKotlin(): String {

        // if string is implementation("..."), this will extract only the ...
        val fullLineExp = "\"org.jetbrains.kotlin:kotlin-.*(?=\\))".toRegex()

        val removeExp = "(?!org.jetbrains.kotlin:kotlin)-.*".toRegex()

        var shouldImportKotlinCompiler = false

        val newText = this.replace(fullLineExp) { isolatedLine ->

            // drop first "-" and remove last "
            val substring = (removeExp.find(isolatedLine.value)?.value ?: "").drop(1).replace("\"", "")

            val splittedSubstring = substring.split(":")

            if ("stdlib" in substring) {
                shouldImportKotlinCompiler = true
                "kotlin(\"stdlib\", KotlinCompilerVersion.VERSION)"
            } else if (splittedSubstring.size == 2) {
                "kotlin(\"${splittedSubstring[0]}\", version = \"${splittedSubstring[1]}\")"
            } else {
                "kotlin(\"${splittedSubstring[0]}\")"
            }
        }

        return if (shouldImportKotlinCompiler) {
            "import org.jetbrains.kotlin.config.KotlinCompilerVersion\n\n" + newText
        } else {
            newText
        }
    }


    // apply(plugin = "com.trello.victor")
// becomes within plugin{}
// id("com.trello.victor")
    fun String.convertPluginsIntoOneBlock(): String {

        // group plugin expressions. There can't be any space or tabs on the start of the line, else the regex will fail.
        // ok example:
        // apply(...)
        // apply(...)
        //
        // not ok example:
        // apply(...)
        //    apply(...)
        val fullLineExp = "(apply\\(plugin\\s*=\\s*\".*\"\\)[\\s\\S]){2,}".toRegex()

        val isolatedId = "\".*\"(?=\\))".toRegex()

        return this.replace(fullLineExp) { isolatedLine ->
            // this will fold the ids into a single string
            val plugins = isolatedId.findAll(isolatedLine.value)?.fold("") { acc, matchResult ->
                acc + "    id(${matchResult.value})\n"
            }
            "plugins {\n$plugins}\n"
        }
    }

    // testImplementation(group: "junit", name: "junit", version: "4.12")
// becomes
// testImplementation(group = "junit", name = "junit", version = "4.12")
    fun String.replaceColonWithEquals(): String {

        // this get "group:"
        val expression = "\\w*:\\s*\".*?\"".toRegex()

        return this.replace(expression) {
            it.value.replace(":", " =")
        }
    }
}
