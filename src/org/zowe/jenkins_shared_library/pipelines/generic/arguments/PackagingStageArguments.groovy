/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.pipelines.generic.arguments

import java.util.concurrent.TimeUnit
import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout

/**
 * Represents the arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#packagingGeneric(java.util.Map)} method.
 */
class PackagingStageArguments extends GenericStageArguments {
    /**
     * Amount of time allowed for the SonarQube scan
     *
     * @default 30 Minutes
     */
    StageTimeout timeout = [time: 30, unit: TimeUnit.MINUTES]

    /**
     * The name of the package step.
     *
     * @Note No special characters allowed, only letters and numbers.
     *
     * Example: explorer-jes
     */
    String name

    /**
     * Local workspace folder.
     *
     * @Default {@link jenkins_shared_library.package.Pax#DEFAULT_LOCAL_WORKSPACE}
     */
    String localWorkspace

    /**
     * Remote workspace folder
     */
    String remoteWorkspace

    /**
     * PAX command line options
     */
    String paxOptions
}
