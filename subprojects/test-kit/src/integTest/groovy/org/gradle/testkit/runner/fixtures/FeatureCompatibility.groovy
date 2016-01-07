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

import org.gradle.testkit.runner.GradleRunnerIntegrationTest
import org.gradle.testkit.runner.fixtures.annotations.CaptureBuildOutputInDebug
import org.gradle.testkit.runner.fixtures.annotations.CaptureExecutedTasks
import org.gradle.testkit.runner.fixtures.annotations.PluginClasspathInjection
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.util.GradleVersion

import java.lang.annotation.Annotation

enum FeatureCompatibility {
    CAPTURE_EXECUTED_TASKS(CaptureExecutedTasks, TestKitFeature.CAPTURE_BUILD_RESULT_TASKS.since),
    PLUGIN_CLASSPATH_INJECTION(PluginClasspathInjection, TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since),
    CAPTURE_BUILD_OUTPUT_IN_DEBUG(CaptureBuildOutputInDebug, TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since)

    private final Class<? extends Annotation> feature
    private final GradleVersion since

    private FeatureCompatibility(Class<? extends Annotation> feature, GradleVersion since) {
        this.feature = feature
        this.since = since
        assert isValidVersion(since, GradleRunnerIntegrationTest.MIN_TESTED_VERSION) : "Feature version $since needs to be later than $GradleRunnerIntegrationTest.MIN_TESTED_VERSION"
    }

    private static boolean isValidVersion(GradleVersion comparedVersion, GradleVersion minVersion) {
        comparedVersion.compareTo(minVersion) >= 0
    }

    static GradleVersion getMinSupportedVersion(Class<? extends Annotation> feature) {
        FeatureCompatibility featureCompatibility = values().find { it.feature == feature }

        if (!feature) {
            throw new IllegalArgumentException("Unsupported feature annotation '$feature'")
        }

        featureCompatibility.since
    }

    static boolean isSupported(Class<? extends Annotation> feature, GradleVersion version) {
        isValidVersion(version, getMinSupportedVersion(feature))
    }
}
