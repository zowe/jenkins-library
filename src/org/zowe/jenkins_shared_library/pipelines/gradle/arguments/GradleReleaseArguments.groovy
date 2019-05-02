/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.gradle.arguments

import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.ReleaseStageArguments

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.Gradle.GradlePipeline#release(org.zowe.jenkins_shared_library.pipelines.Gradle.arguments.GradleReleaseArguments)}
 * method.
 */
class GradleReleaseArguments extends ReleaseStageArguments {

    /**
     * Artifactory username project variable defined in gradle settings
     */
    String artifactoryReleaseUsernameVar

    /**
     * Artifactory password project variable defined in gradle settings.
     */
    String artifactoryReleasePasswordVar
}
