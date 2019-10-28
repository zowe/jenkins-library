/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.pipelines.gradle.arguments

import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments

/**
 * Arguments available to the
 * {@link jenkins_shared_library.pipelines.Gradle.GradlePipeline#sonarScanGradle(jenkins_shared_library.pipelines.Gradle.arguments.SonarScanStageArguments)}
 * method.
 */
class GradleSonarScanStageArguments extends SonarScanStageArguments {
    /**
     * If disable usage of SonarQube Gradle Plugin.
     *
     * @Note SonarQube gradle plugin doesn't support branch/pull request scanning, so
     *       if you want to {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments#allowBranchScan},
     *       you should set this to true and provide sonar-project.properties.
     *
     * @default {@code false}
     */
    Boolean disableSonarGradlePlugin = false
}

