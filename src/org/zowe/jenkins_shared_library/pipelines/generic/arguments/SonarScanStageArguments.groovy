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
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#sonarScanGeneric(java.util.Map)} method.
 */
class SonarScanStageArguments extends GenericStageArguments {
    /**
     * Amount of time allowed for the SonarQube scan
     *
     * @default 5 Minute
     */
    StageTimeout timeout = [time: 5, unit: TimeUnit.MINUTES]

    /**
     * The name of the SonarQube Scan step.
     *
     * @default {@code (empty)}
     */
    String name = ""

    /**
     * SonarQube scanner tool name defined in Jenkins.
     *
     * @Note This argument is required if {@link #operation} is not provided.
     */
    String scannerTool

    /**
     * SonarQube scanner server name defined in Jenkins
     *
     * @Note This argument is required if {@link #operation} is not provided.
     */
    String scannerServer
}
