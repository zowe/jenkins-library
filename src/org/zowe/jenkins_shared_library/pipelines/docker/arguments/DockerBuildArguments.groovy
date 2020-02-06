/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.docker.arguments

import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments

import java.util.concurrent.TimeUnit

/**
 * Represents the arguments available to the
 * {@link jenkins_shared_library.pipelines.docker.DockerPipeline#buildDocker(java.util.Map)} method.
 */
class DockerBuildArguments extends BuildStageArguments {
    /**
     * Amount of time allowed for the Docker build stage
     *
     * @default 1 Hour
     */
    StageTimeout timeout = [time: 1, unit: TimeUnit.HOURS]

    /**
     * The name of the build step.
     *
     * @default {@code "Dockerfile"}
     */
    String name = "Dockerfile"

    /**
     * Docker file path and name
     */
    String dockerFile

    /**
    *  Extra command arguments
    */ 
    String buildArgs
}
