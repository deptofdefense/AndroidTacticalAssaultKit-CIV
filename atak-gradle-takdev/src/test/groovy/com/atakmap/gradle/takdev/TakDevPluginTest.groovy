package com.atakmap.gradle.takdev

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

// taken from https://docs.gradle.org/current/userguide/test_kit.html with changes
class TakDevPluginTest extends Specification {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    private ArrayList<File> testFiles = new ArrayList<>()

    private String gradle_settings = """
            """

    private String build_gradle = """
            plugins {
              id 'com.atakmap.gradle.takdev.TakDevPlugin'
            }
            """

    private String local_properties = """
            """

    private BuildResult gradle(boolean isSuccessExpected, String[] arguments = ['tasks']) {
        arguments += '--stacktrace'
        def runner = GradleRunner.create()
                .withArguments(arguments)
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
        return isSuccessExpected ? runner.build() : runner.buildAndFail()
    }

    private BuildResult gradle(String[] arguments = ['tasks']) {
        gradle(true, arguments)
    }

    def setup() {
        File settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << gradle_settings
        testFiles.add(settingsFile)
        File buildGradle = testProjectDir.newFile('build.gradle')
        buildGradle << build_gradle
        testFiles.add(buildGradle)
        File localProperties = testProjectDir.newFile('local.properties')
        localProperties << local_properties
        testFiles.add(localProperties)
    }

    def "testTask"() {
        when:
        def result = gradle(['tasks'] as String[])
        then:
        println(result.output)
        result.tasks.each {
            println("'${it.path}' => ${it.outcome}")
        }
    }
}
