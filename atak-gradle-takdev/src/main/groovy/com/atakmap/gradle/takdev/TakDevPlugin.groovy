package com.atakmap.gradle.takdev

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class TakDevPlugin implements Plugin<Project> {

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
        verbose = getLocalOrProjectProperty(project, 'takdev.verbose', 'false').equals('true')

        // Consuming gradle project already handles 'takrepoUrl', 'takrepoUser' and 'takrepoPassword'
        project.ext.mavenOnly = getLocalOrProjectProperty(project, 'takrepo.force', 'false').equals('true')
        project.ext.snapshot = getLocalOrProjectProperty(project, 'takrepo.snapshot', 'true').equals('true')
        project.ext.devkitVersion = getLocalOrProjectProperty(project, 'takrepo.devkit.version', project.ATAK_VERSION)
        project.ext.takdevProduction = getLocalOrProjectProperty(project, 'takdev.production', 'false').equals('true')
        project.ext.takdevNoApp = getLocalOrProjectProperty(project, 'takdev.noapp', 'false').equals('true')

        def haveRemoteRepo = !((null == project.takrepoUrl) || (null == project.takrepoUser) || (null == project.takrepoPassword))

        debugPrintln("isDevKitEnabled => ${project.isDevKitEnabled()}")
        debugPrintln("takrepo URL option => ${project.takrepoUrl}")
        debugPrintln("takrepo user option => ${project.takrepoUser}")
        debugPrintln("mavenOnly option => ${project.mavenOnly}")
        debugPrintln("haveRemoteRepo => ${haveRemoteRepo}")
        debugPrintln("snapshot option => ${project.snapshot}")
        debugPrintln("devkitVersion option => ${project.devkitVersion}")
        debugPrintln("production option => ${project.takdevProduction}")
        debugPrintln("noapp option => ${project.takdevNoApp}")

        if (project.mavenOnly) {
            if (haveRemoteRepo) {
                configureMaven(project)
            } else {
                throw new GradleException("No remote repo available to configure TAK DevKit as mavenOnly")
            }
        } else {
            def tuple = getAutoBuilder(project)
            if (null == tuple) {
                tuple = getOfflineDevKit(project)
            } else {
                println("Configuring Autobuilder TAK plugin build")
                configureOffline(project, tuple)
            }
            if (null == tuple) {
                if (haveRemoteRepo) {
                    println("Configuring Maven TAK plugin build")
                    configureMaven(project)
                } else {
                    throw new GradleException("No remote repp or local files available to configure TAK DevKit")
                }
            } else {
                println("Configuring Offline DevKit TAK plugin build")
                configureOffline(project, tuple)
            }
        }

        // define tasks
        addTasks(project)
    }

    void addTasks(Project project) {
        project.ext.getTargetVersion = project.tasks.register('getTargetVersion', DefaultTask) {
            group 'metadata'
            description 'Gets this plugin\'s targeted ATAK version'
            doLast {
                println(project.devkitVersion)
            }
        }
    }

    static String getLocalOrProjectProperty(Project project, String key, String defval) {
        if (new File('local.properties').exists()) {
            def localProperties = new Properties()
            localProperties.load(project.rootProject.file('local.properties').newDataInputStream())
            def value = localProperties.get(key)
            if ((null != value)) {
                return value
            }
        }
        return project.properties.get(key, defval)
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
                getLocalOrProjectProperty(project, 'sdk.path', "${project.rootDir}/sdk")
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

        project.android.applicationVariants.all { variant ->

            Dependency dep = project.dependencies.create(project.files(tuple.apiJar.absolutePath))
            project.dependencies.add("${variant.name}CompileOnly", dep)
            project.dependencies.add("test${variant.name.capitalize()}Implementation", dep)

            def compileProvider = project.tasks.named("compile${variant.name.capitalize()}JavaWithJavac")
            compileProvider.configure({
                doFirst {
                    def mappingName = "proguard-${variant.flavorName}-${variant.buildType.name}-mapping.txt"
                    def mappingFqn = tuple.mapping.absolutePath
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

            // inject keystore before validate signing
            def signingProvider = project.tasks.named("validateSigning${variant.name.capitalize()}")
            signingProvider.configure({
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
            })
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

        project.android.applicationVariants.all { variant ->

            // arbitrary, variant specific, configuration names
            def apkZipConfigName = "${variant.name}ApkZip"
            def mappingConfigName = "${variant.name}Mapping"
            def keystoreConfigName = "${variant.name}Keystore"

            def devType = variant.buildType.matchingFallbacks[0] ?: variant.buildType.name
            if (project.takdevProduction && ('release' == variant.buildType.name)) {
                devType = variant.buildType.name
            }
            def devFlavor = variant.productFlavors.matchingFallbacks[0][0] ?: variant.flavorName

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

            // api
            project.dependencies.add("${variant.name}CompileOnly", mavenCoord)
            // test
            project.dependencies.add("test${variant.name.capitalize()}Implementation", mavenCoord)

            // other artifacts are strongly typed per variant
            mavenCoord.group = mavenGroupTyped

            // add the APK zip as a dependency
            def apkZipConfiguration
            if (!project.takdevNoApp) {
                mavenCoord.name = 'apk'
                apkZipConfiguration = project.configurations.register(apkZipConfigName)
                project.dependencies.add(apkZipConfigName, mavenCoord)
                debugPrintln("${variant.name} => Using repository APK, ${mavenCoord}")
                if ('civ' != variant.flavorName) {
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

            def compileProvider = project.tasks.named("compile${variant.name.capitalize()}JavaWithJavac")
            compileProvider.configure({
                doFirst {
                    // Proguard mapping; flavor specific
                    def mappingName = "proguard-${variant.flavorName}-${variant.buildType.name}-mapping.txt"
                    def mappingFqn = "${project.buildDir}/${mappingName}"
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

            // inject keystore before signing
            def signingProvider = project.tasks.named("validateSigning${variant.name.capitalize()}")
            signingProvider.configure({
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
            })
        }
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
        def depKey  = "${depcoord['group']}.${depcoord['name']}"
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
        Configuration config = null
        try {
            config = rootProject.configurations.create("depResolver")
            config.dependencies.add(dep.copy())
            boolean resolved
            try {
                def deps = config.resolve()
                resolved = (null != deps) && !deps.empty
            } catch (Exception e) {
                // dependency resolution failed
                resolved = false
            }
            // update the cached dependency resolution state
            writePluginProperty(p, "${depKey}.hash", repoHash)
            writePluginProperty(p, "${depKey}.available", resolved ? 'true' : 'false')
            return resolved
        } finally {
            if (config != null)
                rootProject.configurations.remove(config)
        }
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
}
