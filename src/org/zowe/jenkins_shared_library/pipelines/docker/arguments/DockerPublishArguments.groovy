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

import org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments

import java.util.concurrent.TimeUnit

/**
 * Represents the arguments available to the
 * {@link jenkins_shared_library.pipelines.docker.DockerPipeline#publishDocker(java.util.Map)} method.
 */
class DockerPublishArguments extends PublishStageArguments {
    /**
     * Amount of time allowed for the Docker publish stage
     *
     * @default 10 Minutes
     */
    StageTimeout timeout = [time: 10, unit: TimeUnit.MINUTES]

    /**
     * The name of the publishing step.
     *
     * @default {@code "Image"}
     */
    String name = "Image"

    /**
     * Docker image name
     */
    String image

    /**
     * If we allow publishing if the pipeline doesn't define any test stages.
     *
     * @Note Usually we don't have tests for Docker pipeline, so default to {@code true}.
     *
     * @Default {@code true}
     */
    Boolean allowPublishWithoutTest = true
}
