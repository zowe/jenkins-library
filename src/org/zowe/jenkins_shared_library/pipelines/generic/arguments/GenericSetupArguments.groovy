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
 * Arguments available to the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#setupGeneric(org.zowe.jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments)}
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
     * Amount of time allowed for the SonarQube scan
     *
     * @default 5 Minute
     */
    StageTimeout sonarQubeScan = [time: 5, unit: TimeUnit.MINUTES]

    /**
     * Github configurations
     *
     * Use configurations defined at {@link org.zowe.jenkins_shared_library.scm.GitHub#init}.
     */
    Map github

    /**
     * Artifactory configurations
     *
     * Use configurations defined at {@link org.zowe.jenkins_shared_library.artifact.JFrogArtifactory#init}.
     */
    Map artifactory

    /**
     * PAX server configurations
     *
     * Use configurations defined at {@link org.zowe.jenkins_shared_library.package.Pax#init}.
     */
    Map pax

    /**
     * Extra init operations
     *
     * This closure will execute in 'Init Generic Pipeline' stage, and the pipeline object will be
     * passed to Clusure as arguments.
     */
    Closure extraInit
}
