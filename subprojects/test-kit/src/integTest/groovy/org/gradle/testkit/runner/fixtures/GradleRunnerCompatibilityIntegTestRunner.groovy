/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testkit.runner.fixtures

import groovy.transform.Sortable
import groovy.transform.TupleConstructor
import org.gradle.integtests.fixtures.AbstractMultiTestRunner
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.testkit.runner.fixtures.annotations.*
import org.gradle.testkit.runner.internal.dist.GradleDistribution
import org.gradle.testkit.runner.internal.dist.InstalledGradleDistribution
import org.gradle.testkit.runner.internal.dist.VersionBasedGradleDistribution
import org.gradle.util.GradleVersion

import java.lang.annotation.Annotation

/**
 * Verifies GradleRunner functionality against "supported" Gradle versions.
 *
 * Compatibility testing is performed against a subset of Gradle versions to limit the (growing) number of required test executions:
 *
 * - The minimum Gradle version a feature is compatible with (if no specific feature is provided, then the minimum Gradle version is used that supports TestKit)
 * - The most recent released Gradle version
 * - The Gradle version under development
 *
 * The range of versions used for compatibility testing can be controlled with the system property "org.gradle.integtest.testkit.compatibility".
 */
class GradleRunnerCompatibilityIntegTestRunner extends GradleRunnerIntegTestRunner {
    /**
     * Read by tests to configure themselves to determine the Gradle version used for test execution.
     */
    public static GradleVersion gradleVersion

    /**
     * Read by tests to configure themselves to determine the Gradle distribution used for test execution.
     */
    public static GradleDistribution distribution

    /**
     * TestKit features annotations read by tests to determine the minimum compatible Gradle version.
     */
    private static final List<? extends Annotation> TESTKIT_FEATURES = [CaptureExecutedTasks, PluginClasspathInjection]

    /**
     * The minimum Gradle version used for testing TestKit if no other features require an earlier version.
     */
    public static final GradleVersion MIN_TESTED_VERSION = GradleVersion.version('1.0')

    private static final String COMPATIBILITY_SYSPROP_NAME = 'org.gradle.integtest.testkit.compatibility'
    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()

    GradleRunnerCompatibilityIntegTestRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        determineTestedGradleDistributions().each { testedGradleDistribution ->
            def releasedDist = RELEASED_VERSION_DISTRIBUTIONS.getDistribution(testedGradleDistribution.gradleVersion)
            addExecutions(releasedDist, testedGradleDistribution)
        }
    }

    private Set<TestedGradleDistribution> determineTestedGradleDistributions() {
        if (target.getAnnotation(NonCrossVersion)) {
            return [TestedGradleDistribution.underDevelopment()] as Set
        }

        String version = System.getProperty(COMPATIBILITY_SYSPROP_NAME, 'all')

        switch (version) {
            case 'all': return [TestedGradleDistribution.forVersion(getMinCompatibleVersion()),
                                TestedGradleDistribution.mostRecentFinalRelease(),
                                TestedGradleDistribution.underDevelopment()] as SortedSet
            case 'current': return [TestedGradleDistribution.underDevelopment()] as Set
            default: throw new IllegalArgumentException("Invalid value for $COMPATIBILITY_SYSPROP_NAME system property: $version (valid values: 'all', 'current')")
        }
    }

    private void addExecutions(releasedDist, TestedGradleDistribution testedGradleDistribution) {
        if (releasedDist && !releasedDist.worksWith(Jvm.current())) {
            add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current JVM'))
        } else if (releasedDist && !releasedDist.isToolingApiTargetJvmSupported(Jvm.current().javaVersion)) {
            add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current JVM due to an incompatibility with the tooling API'))
        } else if (releasedDist && !releasedDist.worksWith(OperatingSystem.current())) {
            add(new IgnoredGradleRunnerExecution(testedGradleDistribution, 'does not work with current OS'))
        } else {
            if (target.getAnnotation(NoDebug)) {
                add(new GradleRunnerExecution(testedGradleDistribution, false))
            } else if (target.getAnnotation(Debug)) {
                add(new GradleRunnerExecution(testedGradleDistribution, true))
            } else {
                [true, false].each { add(new GradleRunnerExecution(testedGradleDistribution, it)) }
            }
        }
    }

    private GradleVersion getMinCompatibleVersion() {
        List<GradleVersion> testedFeatures = TESTKIT_FEATURES.findAll { target.getAnnotation(it) }.collect { FeatureCompatibility.getMinSupportedVersion(it) }
        !testedFeatures.empty ? testedFeatures.min() : MIN_TESTED_VERSION
    }

    @TupleConstructor
    @Sortable(includes = ['gradleVersion'])
    private static class TestedGradleDistribution {

        private static
        final TestedGradleDistribution underDevelopmentTestedGradleDistribution = new TestedGradleDistribution(BUILD_CONTEXT.version, new InstalledGradleDistribution(BUILD_CONTEXT.gradleHomeDir))

        final GradleVersion gradleVersion
        final GradleDistribution gradleDistribution

        static TestedGradleDistribution forVersion(GradleVersion gradleVersion) {
            new TestedGradleDistribution(gradleVersion, new VersionBasedGradleDistribution(gradleVersion.version))
        }

        static TestedGradleDistribution mostRecentFinalRelease() {
            new TestedGradleDistribution(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version,
                new VersionBasedGradleDistribution(RELEASED_VERSION_DISTRIBUTIONS.mostRecentFinalRelease.version.version))
        }

        static TestedGradleDistribution underDevelopment() {
            underDevelopmentTestedGradleDistribution
        }
    }

    private static class IgnoredGradleRunnerExecution extends AbstractMultiTestRunner.Execution {

        private final TestedGradleDistribution testedGradleDistribution
        private final String reason

        IgnoredGradleRunnerExecution(TestedGradleDistribution testedGradleDistribution, String reason) {
            this.testedGradleDistribution = testedGradleDistribution
            this.reason = reason
        }

        @Override
        protected String getDisplayName() {
            "$testedGradleDistribution.gradleVersion.version $reason"
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            false
        }
    }

    private static class GradleRunnerExecution extends GradleRunnerIntegTestRunner.GradleRunnerExecution {

        private final TestedGradleDistribution testedGradleDistribution

        GradleRunnerExecution(TestedGradleDistribution testedGradleDistribution, boolean debug) {
            super(debug)
            this.testedGradleDistribution = testedGradleDistribution
        }

        @Override
        protected String getDisplayName() {
            "version = $testedGradleDistribution.gradleVersion.version, debug = $debug"
        }

        @Override
        protected void before() {
            super.before()
            GradleRunnerCompatibilityIntegTestRunner.gradleVersion = testedGradleDistribution.gradleVersion
            GradleRunnerCompatibilityIntegTestRunner.distribution = testedGradleDistribution.gradleDistribution
        }

        @Override
        protected boolean isTestEnabled(AbstractMultiTestRunner.TestDetails testDetails) {
            if (testDetails.getAnnotation(NonCrossVersion) && testedGradleDistribution != TestedGradleDistribution.underDevelopment()) {
                return false
            }

            if (isDebugModeAndBuildOutputCapturedButVersionUnsupported(testDetails)) {
                return false
            }

            super.isTestEnabled(testDetails)
        }

        private boolean isDebugModeAndBuildOutputCapturedButVersionUnsupported(AbstractMultiTestRunner.TestDetails testDetails) {
            CaptureBuildOutputInDebug captureBuildOutputInDebug = testDetails.getAnnotation(CaptureBuildOutputInDebug)
            debug && captureBuildOutputInDebug && !FeatureCompatibility.isSupported(CaptureBuildOutputInDebug, testedGradleDistribution.gradleVersion)
        }
    }
}
