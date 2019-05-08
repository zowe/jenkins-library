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
     */
    Map github

    /**
     * Artifactory configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.artifact.JFrogArtifactory#init(Map)}.</p>
     */
    Map artifactory

    /**
     * PAX server configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.package.Pax#init(Map)}.</p>
     */
    Map pax

    /**
     * Extra init operations
     *
     * <p>This closure will execute in 'Init Generic Pipeline' stage, and the pipeline object will be
     * passed to Clusure as arguments.</p>
     */
    Closure extraInit
}
