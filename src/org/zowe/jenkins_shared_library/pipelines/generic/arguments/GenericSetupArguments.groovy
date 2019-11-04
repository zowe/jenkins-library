/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic.arguments

import org.zowe.jenkins_shared_library.pipelines.base.arguments.SetupArguments
import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the {@link jenkins_shared_library.pipelines.generic.GenericPipeline#setupGeneric(jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments)}
 * method.
 */
class GenericSetupArguments extends SetupArguments {
    /**
     * Amount of time allowed for the CI Skip check.
     *
     * @default 1 Minute
     */
    StageTimeout ciSkip = [time: 1, unit: TimeUnit.MINUTES]

    /**
     * Amount of time allowed for the init pipeline
     *
     * @default 1 Minute
     */
    StageTimeout initForGeneric = [time: 1, unit: TimeUnit.MINUTES]

    /**
     * Github configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.scm.GitHub#init(Map)}.</p>
     *
     * @Note If this value is not provide, the library will try to init a GitHub object with default configurations.
     */
    Map github

    /**
     * If the pipeline doesn't need {@link #github} at all.
     *
     * @Note Set this to {@code true} to prevent the library to load default GitHub configurations.
     *
     * @default {@code false}
     */
    Boolean disableGithub = false

    /**
     * Artifactory configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.artifact.JFrogArtifactory#init(Map)}.</p>
     *
     * @Note If this value is not provide, the library will try to init a Artifactory object with default configurations.
     */
    Map artifactory

    /**
     * If the pipeline doesn't need {@link #artifactory} at all.
     *
     * @Note Set this to {@code true} to prevent the library to load default Artifactory configurations.
     *
     * @default {@code false}
     */
    Boolean disableArtifactory = false

    /**
     * PAX server configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.package.Pax#init(Map)}.</p>
     *
     * @Note If this value is not provide, the library will try to init a Pax object with default configurations.
     */
    Map pax

    /**
     * If the pipeline doesn't need {@link #pax} at all.
     *
     * @Note Set this to {@code true} to prevent the library to load default Pax configurations.
     *
     * @default {@code false}
     */
    Boolean disablePax = false

    /**
     * Extra init operations
     *
     * <p>This closure will execute in 'Init Generic Pipeline' stage, and the pipeline object will be
     * passed to Clusure as arguments.</p>
     */
    Closure extraInit
}
