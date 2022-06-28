package com.atakmap.gradle.takdev

import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import java.io.ByteArrayOutputStream

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class TakDevPlugin implements Plugin<Project> {

    enum VariantType {
        UNKNOWN,
        APPLICATION,
        LIBRARY
    }

    class PathTuple {
        File apiJar
        File keystore
        File mapping

        @Override
        String toString() {
            return "apiJar => ${apiJar.absolutePath}, keystore => ${keystore.absolutePath}, mapping => ${mapping.absolutePath}"
        }
    }

    boolean verbose = false

    void debugPrintln(Object msg) {
        if (verbose)
            println(msg)
    }

    void apply(Project project) {

        project.ext.devkitVersion = getLocalOrProjectProperty(project, 'takrepo.devkit.version', 'devkitVersion', project.ATAK_VERSION)
        if (0 > versionComparator(project.devkitVersion, '4.2.0')) {
            throw new GradleException("Incompatible takdev version. This plugin should be major version 1 to support ${project.devkitVersion}")
        }

        verbose = getLocalOrProjectProperty(project, 'takdev.verbose', null, 'false').equals('true')

        String appVariant = VariantType.APPLICATION == getVariantType(project) ? 'true' : 'false'
        String libVariant = VariantType.LIBRARY == getVariantType(project) ? 'true' : 'false'

        // Consuming gradle project already handles 'isDevKitEnabled', 'takrepoUrl', 'takrepoUser' and 'takrepoPassword'
        project.ext.mavenOnly = getLocalOrProjectProperty(project, 'takrepo.force', 'mavenOnly', 'false').equals('true')
        project.ext.snapshot = getLocalOrProjectProperty(project, 'takrepo.snapshot', 'snapshot', 'true').equals('true')
        project.ext.sdkPath = getLocalOrProjectProperty(project, 'sdk.path', 'sdkPath', "${project.rootDir}/sdk")
        project.ext.takdevProduction = getLocalOrProjectProperty(project, 'takdev.production', 'takdevProduction', 'false').equals('true')
        project.ext.takdevNoApp = getLocalOrProjectProperty(project, 'takdev.noapp', 'takdevNoApp', libVariant).equals('true')
        project.ext.takdevConTestEnable = getLocalOrProjectProperty(project, 'takdev.contest.enable', 'takdevConTestEnable', appVariant).equals('true')
        project.ext.takdevConTestVersion = getLocalOrProjectProperty(project, 'takdev.contest.version', 'takdevConTestVersion', project.devkitVersion)
        project.ext.takdevConTestPath = getLocalOrProjectProperty(project, 'takdev.contest.path', 'takdevConTestPath', "${project.rootDir}/espresso")

        // Ideally, the isDevKitEnabled variable should be renamed, as it indicates whether we're using a remote repository.
        // However, there are now a number of plugins that have this variable name through deep copies of plugintemplate
        debugPrintln("isDevKitEnabled => ${project.isDevKitEnabled()}")
        debugPrintln("takrepo URL option => ${project.takrepoUrl}")
        debugPrintln("takrepo user option => ${project.takrepoUser}")
        debugPrintln("mavenOnly option => ${project.mavenOnly}")
        debugPrintln("snapshot option => ${project.snapshot}")
        debugPrintln("devkitVersion option => ${project.devkitVersion}")
        debugPrintln("sdkPath option => ${project.sdkPath}")
        debugPrintln("production option => ${project.takdevProduction}")
        debugPrintln("noapp option => ${project.takdevNoApp}")
        debugPrintln("connected test enable => ${project.takdevConTestEnable}")
        debugPrintln("connected test version => ${project.takdevConTestVersion}")
        debugPrintln("connected test path => ${project.takdevConTestPath}")

        if (project.mavenOnly) {
            if (project.isDevKitEnabled) {
                configureMaven(project)
            } else {
                throw new GradleException("No remote repo available to configure TAK DevKit as mavenOnly")
            }
        } else {
            def tuple = getAutoBuilder(project)
            boolean offline = null == tuple
            if (offline) {
                tuple = getOfflineDevKit(project)
            }
            if (null == tuple) {
                if (project.isDevKitEnabled) {
                    println("Configuring Maven TAK plugin build")
                    configureMaven(project)
                } else {
                    throw new GradleException("No remote repp or local files available to configure TAK DevKit")
                }
            } else {
                offline ? println("Configuring Offline TakDev plugin build") :
                        println("Configuring Autobuilder TakDev plugin build")
                configureOffline(project, tuple)
            }
        }

        // define tasks
        addTasks(project)
        addUtilities(project)
    }

    static void addTasks(Project project) {
        project.ext.getTargetVersion = project.tasks.register('getTargetVersion', DefaultTask) {
            group 'metadata'
            description 'Gets this plugin\'s targeted ATAK version'
            doLast {
                println(project.devkitVersion)
            }
        }
    }

    static String getGitInfo(Project project, List arg_list) throws Exception {
        def info = ""
        new ByteArrayOutputStream().withStream { os ->
            project.exec {
                executable = 'git'
                args = arg_list
                standardOutput = os
                ignoreExitValue = true
            }
            info = os.toString().trim()
        }
        return info
    }

    static void addUtilities(project) {

        // Attempt to get a suitable version name for the plugin based on
        // either a git or svn repository
        project.ext.getVersionName = {
            def version = "1"
            try {
                version = getGitInfo(project, ['rev-parse', '--short=8', 'HEAD'])
                println("versionName[git]: $version")
            } catch (Exception ignored) {
                println("error occured, using version of $version")
            }
            return version
        }

        // Attempt to get a suitable version code for the plugin based on
        // either a git or svn repository
        project.ext.getVersionCode = {
            def revision = 1
            try {
                String outputAsString = getGitInfo(project, ['show', '-s', '--format=%ct'])
                revision = outputAsString.toInteger()
                println("version[git]: $revision")
            } catch (Exception ignored) {
                println("error occured, using revision of $revision")
            }
            return revision
        }
    }

    static String getLocalOrProjectProperty(Project project, String localKey, String projectKey, String defval) {
        if (new File('local.properties').exists()) {
            def localProperties = new Properties()
            localProperties.load(project.rootProject.file('local.properties').newDataInputStream())
            def value = localProperties.get(localKey)
            if ((null != value)) {
                return value
            }
        }
        return project.properties[localKey] ?: String.valueOf(project.properties.get(projectKey, defval))
    }

    static String resolvePathFromSet(String[] filePaths, String fileName) {
        for (String path : filePaths) {
            File candidate = new File(path, fileName)
            if (candidate.exists()) {
                return path
            }
        }
        return null
    }

    PathTuple getAutoBuilder(Project project) {
        def tuple = new PathTuple()
        tuple.apiJar = new File("${project.rootDir}/../../ATAK/app/build/libs/main.jar")
        tuple.keystore = new File("${project.rootDir}/../../android_keystore")
        tuple.mapping = new File("${project.rootDir}/../../ATAK/app/build/outputs/mapping/release/mapping.txt")

        // mapping does not have to exist for local debug builds
        return tuple.apiJar.exists() && tuple.keystore.exists() ? tuple : null
    }

    PathTuple getOfflineDevKit(Project project) {
        String[] apiPaths = [
                "${project.rootDir}/../..",
                project.sdkPath
        ]
        def offlinePath = resolvePathFromSet(apiPaths, 'main.jar')
        def tuple = new PathTuple()
        tuple.apiJar = new File("${offlinePath}/main.jar")
        tuple.keystore = new File("${offlinePath}/android_keystore")
        tuple.mapping = new File("${offlinePath}/mapping.txt")

        // mapping does not have to exist and a blank will be created with a warning.
        return tuple.apiJar.exists() ? tuple : null
    }

    void configureOffline(Project project, PathTuple tuple) {

        debugPrintln(tuple)

        // Connected test support
        if (project.takdevConTestEnable && !project.takdevConTestPath.isEmpty()) {
            def contestFile = "${project.takdevConTestPath}/testSetup.gradle"
            if (new File(contestFile).exists()) {
                project.apply(from: contestFile)
                debugPrintln("Resolved and applied connected test artifacts from local path, ${project.takdevConTestPath}")
            } else {
                println("Warning: local test files not found. Skipping connected tests.")
            }
        }

        def variants = getVariantSet(project)
        variants.all { variant ->

            Dependency dep = project.dependencies.create(project.files(tuple.apiJar.absolutePath))
            project.dependencies.add("${variant.name}CompileOnly", dep)
            project.dependencies.add("test${variant.name.capitalize()}Implementation", dep)
            if ('debug' == variant.buildType.name) {
                project.dependencies.add("${variant.name}AndroidTestCompileClasspath", dep)
            }

            def devFlavor = getDesiredTpcFlavorName(variant)
            def devType = variant.buildType.name
            def mappingName = "proguard-${devFlavor}-${devType}-mapping.txt"
            def mappingFqn = tuple.mapping.absolutePath

            def preBuildProvider = project.tasks.named("pre${variant.name.capitalize()}Build")
            preBuildProvider.configure({
                doFirst {
                    if (new File(mappingFqn).exists()) {
                        project.copy {
                            from mappingFqn
                            into project.buildDir
                            rename {
                                return mappingName
                            }
                        }
                    } else {
                        def mappingFile = project.file(mappingFqn)
                        if (!mappingFile.getParentFile().exists())
                            mappingFile.getParentFile().mkdirs()
                        project.file(mappingFqn).text = ""
                        println("${variant.name} => WARNING: no mapping file could be established, obfuscating just the plugin to work with the development core")
                    }
                    System.setProperty("atak.proguard.mapping", mappingFqn)
                }
            })

            def signingClosure = {
                doFirst {
                    // Keystore
                    def storeName = 'android_keystore'
                    project.copy {
                        from tuple.keystore.absolutePath
                        into project.buildDir
                        rename {
                            return storeName
                        }
                    }
                }
            }

            // inject keystore before validate signing
            ["validateSigning${variant.name.capitalize()}",
             "validateSigning${variant.name.capitalize()}AndroidTest"].each {
                try {
                    def signingProvider = project.tasks.named(it)
                    signingProvider.configure(signingClosure)
                } catch (UnknownTaskException ute) {
                    debugPrintln("Unknown Task, skippiing ${it}.")
                }
            }
        }
    }

    void configureMaven(Project project) {

        // add the maven repo as a dependency
        MavenArtifactRepository takrepo = project.repositories.maven({
            url = project.takrepoUrl
            name = 'takrepo'
            credentials {
                username project.takrepoUser
                password project.takrepoPassword
            }
        })
        project.repositories.add(takrepo)

        int[] versionTokens = splitVersionString(project.devkitVersion)
        String lowerBound = "${versionTokens.join('.')}-SNAPSHOT"
        versionTokens[versionTokens.length - 1] += 1 // increment for upper
        String upperBound = "${versionTokens.join('.')}-SNAPSHOT"
        String mavenVersion = project.snapshot ? "[${lowerBound}, ${upperBound})" : "(${lowerBound}, ${upperBound})"

        // Connected test support
        if (project.takdevConTestEnable && !project.takdevConTestVersion.isEmpty()) {
            def contestCoord = [group: 'com.atakmap.gradle', name: 'atak-connected-test', version: project.takdevConTestVersion]
            def contestDep = project.dependencies.create(contestCoord)
            def detachedConfig = project.configurations.detachedConfiguration(contestDep)
            try {
                def contestFiles = detachedConfig.resolve()
                if (contestFiles.isEmpty()) {
                    println("Warning: Skipping connected tests, no files from maven tuple ${contestCoord}")
                } else {
                    // this path has to be outside of buildDir, because a 'clean' task would other remove the artifacts
                    def contestPath = "${project.rootDir}/espresso"
                    project.copy {
                        from project.zipTree(contestFiles[0])
                        into contestPath
                    }
                    project.apply(from: "${contestPath}/testSetup.gradle")
                    debugPrintln("Resolved and applied connected test artifacts from maven tuple ${contestCoord}")
                }
            } catch (ResolveException re) {
                println("Warning: Skipping connected tests, could not resolve maven tuple ${contestCoord}")
            }
        }

        def variants = getVariantSet(project)
        variants.all { variant ->
            // arbitrary, variant specific, configuration names
            def apkZipConfigName = "${variant.name}ApkZip"
            def mappingConfigName = "${variant.name}Mapping"
            def keystoreConfigName = "${variant.name}Keystore"

            // accommodate uses where variants may not be defined;  default to 'civ'
            def devFlavor = getDesiredTpcFlavorName(variant)
            def devType = variant.buildType.name
            if (!variant.buildType.matchingFallbacks.isEmpty() &&
                    !(project.takdevProduction && ('release' == variant.buildType.name))) {
                devType = variant.buildType.matchingFallbacks[0]
            }
            // if we still have a buildType of 'debug', use the 'sdk' buildType
            if ('debug' == devType) {
                devType = 'sdk'
            }
            // if we still have a non-production buildType of 'release', use the 'odk' buildType
            if (!project.takdevProduction && 'release' == devType) {
                devType = 'odk'
            }

            def mavenGroupApp = 'com.atakmap.app'
            def mavenGroupCommon = "${mavenGroupApp}.${devFlavor}.common"
            def mavenGroupTyped = "${mavenGroupApp}.${devFlavor}.${devType}"

            // The corner stone, the API coordinates
            def mavenCoord = [group: mavenGroupCommon, name: 'api', version: mavenVersion]
            debugPrintln("${variant.name} => Using repository API, ${mavenCoord}")

            // Test artifact resolution.
            if (!tryResolve(project, takrepo, mavenCoord)) {
                println("Warning: Failed to resolve remote for ${variant.name}. Skipping ${variant.name}.")
                return
            }

            // add the Maven API coordinate as a dependency
            project.dependencies.add("${variant.name}CompileOnly", mavenCoord)
            project.dependencies.add("test${variant.name.capitalize()}Implementation", mavenCoord)
            if ('debug' == variant.buildType.name) {
                project.dependencies.add("${variant.name}AndroidTestCompileClasspath", mavenCoord)
            }

            // other artifacts are strongly typed per variant
            mavenCoord.group = mavenGroupTyped

            // add the APK zip as a dependency
            def apkZipConfiguration
            if (!project.takdevNoApp) {
                mavenCoord.name = 'apk'
                apkZipConfiguration = project.configurations.register(apkZipConfigName)
                project.dependencies.add(apkZipConfigName, mavenCoord)
                debugPrintln("${variant.name} => Using repository APK, ${mavenCoord}")
                if ('civ' != devFlavor) {
                    def mavenCivCoord = [group: "${mavenGroupApp}.civ.${devType}", name: mavenCoord.name, version: mavenCoord.version]
                    project.dependencies.add(apkZipConfigName, mavenCivCoord)
                    debugPrintln("${variant.name} => Adding repository APK, ${mavenCivCoord}")
                }
            }

            // add the mapping as a dependency
            mavenCoord.name = 'mapping'
            def mappingConfiguration = project.configurations.register(mappingConfigName)
            project.dependencies.add(mappingConfigName, mavenCoord)
            debugPrintln("${variant.name} => Using repository mapping, ${mavenCoord}")

            mavenCoord.name = 'keystore'
            def keystoreConfiguration = project.configurations.register(keystoreConfigName)
            project.dependencies.add(keystoreConfigName, mavenCoord)
            debugPrintln("${variant.name} => Using repository keystore, ${mavenCoord}")

            // assembleXXX copies APK and mapping artifacts into output directory
            if (!project.takdevNoApp) {
                def assembleProvider = project.tasks.named("assemble${variant.name.capitalize()}")
                assembleProvider.configure({
                    doLast {
                        project.copy {
                            from apkZipConfiguration
                            into "${project.buildDir}/intermediates/atak-zips"
                            eachFile { fcd ->
                                def zipFileTree = project.zipTree(fcd.file)
                                def apkTree = zipFileTree.matching {
                                    include '**/*.apk'
                                }
                                def matcher = (apkTree.singleFile.name =~ /(.+-([a-zA-Z]+))\.apk/)
                                fcd.name = "${matcher[0][1]}.zip"
                                project.copy {
                                    from zipFileTree
                                    into "${project.buildDir}/outputs/atak-apks/${matcher[0][2]}"
                                    exclude "output-metadata.json"
                                }
                            }
                        }
                    }
                })
            }

            def mappingName = "proguard-${devFlavor}-${devType}-mapping.txt"
            def mappingFqn = "${project.buildDir}/${mappingName}"

            def preBuildProvider = project.tasks.named("pre${variant.name.capitalize()}Build")
            preBuildProvider.configure({
                doFirst {
                    // Proguard mapping; flavor specific
                    project.copy {
                        from mappingConfiguration
                        into project.buildDir
                        rename { sourceName ->
                            debugPrintln("${variant.name} => Copied proguard mapping ${sourceName} from repository into ${mappingFqn}")
                            return mappingName
                        }
                    }
                    System.setProperty("atak.proguard.mapping", mappingFqn)
                }
            })

            def signingClosure = {
                doFirst {
                    // Keystore
                    def storeName = 'android_keystore'
                    def storeFqn = "${project.buildDir}/${storeName}"
                    project.copy {
                        from keystoreConfiguration
                        into project.buildDir
                        rename { sourceName ->
                            debugPrintln("${variant.name} => Copied keystore from repository ${sourceName} into ${storeFqn}")
                            return storeName
                        }
                    }
                }
            }

            // inject keystore before validate signing
            ["validateSigning${variant.name.capitalize()}",
             "validateSigning${variant.name.capitalize()}AndroidTest"].each {
                try {
                    def signingProvider = project.tasks.named(it)
                    signingProvider.configure(signingClosure)
                } catch (UnknownTaskException ute) {
                    debugPrintln("Unknown Task, skippiing ${it}.")
                }
            }
        }
    }

    static VariantType getVariantType(Project project) {
        VariantType variantType = VariantType.UNKNOWN
        if (project.plugins.hasPlugin('com.android.application')) {
            variantType = VariantType.APPLICATION
        } else if (project.plugins.hasPlugin('com.android.library')) {
            variantType = VariantType.LIBRARY
        }
        return variantType
    }

    static DomainObjectSet getVariantSet(Project project) {
        DomainObjectSet variants
        switch (getVariantType(project)) {
            case VariantType.APPLICATION:
                variants = project.android.applicationVariants
                break
            case VariantType.LIBRARY:
                variants = project.android.libraryVariants
                break
            default:
                throw new GradleException('Cannot locate either application or library variants')
        }
        return variants
    }

    static String readPluginProperty(Project project, String name, String defaultValue) {
        File pluginConfig = project.rootProject.file('.takdev/plugin.properties')
        if (pluginConfig.exists()) {
            def localProperties = new Properties()
            FileInputStream fis = null
            try {
                fis = new FileInputStream(pluginConfig)
                localProperties.load(fis)
            } finally {
                if (fis != null)
                    fis.close()
            }
            def value = localProperties.get(name)
            if ((null != value)) {
                return value
            }
        }
        return defaultValue
    }

    static void writePluginProperty(Project project, String name, String value) {
        File pluginConfig = project.rootProject.file('.takdev/plugin.properties')
        if (!pluginConfig.exists())
            pluginConfig.getParentFile().mkdirs()
        def localProperties = new Properties()
        if (pluginConfig.exists()) {
            FileInputStream fis = null
            try {
                fis = new FileInputStream(pluginConfig)
                localProperties.load(fis)
            } finally {
                if (fis != null)
                    fis.close()
            }
        }
        if (null != value)
            localProperties.setProperty(name, value)
        else if (localProperties.containsKey(name))
            localProperties.remove(name)
        FileOutputStream fos = null
        try {
            fos = new FileOutputStream(pluginConfig)
            localProperties.store(fos, null)
        } finally {
            if (fos != null)
                fos.close()
        }
    }

    static String computeHash(Project p, MavenArtifactRepository repo) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256")
        def key = [
                repo.credentials.username,
                repo.credentials.password,
                repo.url,
                p.devkitVersion,
                p.snapshot
        ].join(':')
        def hash = digest.digest(key.getBytes(StandardCharsets.UTF_8))
        return Base64.encoder.encodeToString(hash)
    }

    static boolean tryResolve(Project p, MavenArtifactRepository takrepo, Map<?, ?> depcoord) {
        // This is done against the root project
        // to avoid polluting the configuration caching that occurs
        Project rootProject = p.rootProject

        // add the maven repo if it's not already installed
        if (rootProject.repositories.findByName(takrepo.name) == null) {
            rootProject.repositories.add(rootProject.repositories.mavenLocal())
            rootProject.repositories.add(takrepo)
        }

        // check the cached values for dependency resolution
        def depKey = "${depcoord['group']}.${depcoord['name']}"
        def repoHash = computeHash(p, takrepo)
        def dep = rootProject.dependencies.create(depcoord)
        def cachedHash = readPluginProperty(p, "${depKey}.hash", "")
        if (!cachedHash.equals(repoHash)) {
            // if the cached repo hash does not equal the hash for the
            // specified repo, clear the cached dependency resolution state
            writePluginProperty(p, "${depKey}.hash", null)
            writePluginProperty(p, "${depKey}.available", null)
        } else {
            // the hashes are equal, if dependency resolution is cached, return
            // the cached value
            String available = readPluginProperty(p, "${depKey}.available", null)
            if (available != null)
                return available.equals('true')
        }

        // create a transient configuration and attempt to resolve
        Configuration config = p.configurations.detachedConfiguration(dep)
        boolean resolved
        try {
            def deps = config.resolve()
            resolved = (null != deps) && !deps.empty
        } catch (Exception e) {
            // dependency resolution failed
            resolved = false
        }
        // update the cached dependency resolution state
        writePluginProperty(p, "${dep.name}.hash", repoHash)
        writePluginProperty(p, "${dep.name}.available", resolved ? 'true' : 'false')
        return resolved
    }

    static int[] splitVersionString(String version) {
        int[] components
        try {
            components = version.split('\\.').collect {
                it as Integer
            }
        } catch (Exception ex) {
            throw new GradleException("DevKit version, ${version}, is not a valid tuple - stopping build\n${ex.message}")
        }

        return components
    }

    // taken from https://gist.github.com/founddrama/971284 with changes
    static int versionComparator(String a, String b) {
        def VALID_TOKENS = /._/
        a = a.tokenize(VALID_TOKENS)
        b = b.tokenize(VALID_TOKENS)

        for (i in 0..<Math.max(a.size(), b.size())) {
            if (i == a.size()) {
                return b[i].isInteger() ? -1 : 1
            } else if (i == b.size()) {
                return a[i].isInteger() ? 1 : -1
            }

            if (a[i].isInteger() && b[i].isInteger()) {
                int c = (a[i] as int) <=> (b[i] as int)
                if (c != 0) {
                    return c
                }
            } else if (a[i].isInteger()) {
                return 1
            } else if (b[i].isInteger()) {
                return -1
            } else {
                int c = a[i] <=> b[i]
                if (c != 0) {
                    return c
                }
            }
        }
        return 0
    }

	static String getDesiredTpcFlavorName(Object variant) {
        String flavorName = variant.flavorName ?: 'civ'
        def matchingFallbacks = variant.productFlavors.matchingFallbacks
        if (!matchingFallbacks.isEmpty() &&
                !matchingFallbacks[0].isEmpty()) {
            flavorName = matchingFallbacks[0][0]
        }
        return flavorName
    }
}
