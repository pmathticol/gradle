/*
 * Copyright 2019 the original author or authors.
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

package Gradle_Check.configurations

import common.applyDefaultSettings
import configurations.BaseGradleBuildType
import configurations.publishBuildStatusToGithub
import configurations.snapshotDependencies
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import projects.PerformanceTestProject

class PerformanceTestsPass(model: CIBuildModel, performanceTestProject: PerformanceTestProject) : BaseGradleBuildType(model, init = {
    uuid = performanceTestProject.uuid + "_Trigger"
    id = AbsoluteId(uuid)
    name = performanceTestProject.name + " (Trigger)"

    applyDefaultSettings()

    features {
        publishBuildStatusToGithub(model)
    }

    dependencies {
        snapshotDependencies(performanceTestProject.performanceTests)
        performanceTestProject.performanceTests.forEach {
            artifacts(it.id!!) {
                id = "ARTIFACT_DEPENDENCY_${it.id!!}"
                cleanDestination = true
                artifactRules = "results/performance/build/test-results-*.zip!performance-tests/perf-results.json => perf-results/${it.bucketIndex}/"
            }
        }
    }
})
