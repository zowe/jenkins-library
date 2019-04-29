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

/**
 * Represents the arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#sonarScanGeneric(java.util.Map)} method.
 */
class SonarScanStageArguments extends GenericStageArguments {
    /**
     * The name of the SonarQube Scan step.
     *
     * @default {@code ""}
     */
    String name = ""

    /**
     * SonarQube scanner tool name defined in Jenkins
     */
    String scannerTool = 'sonar-scanner-3.2.0'

    /**
     * SonarQube scanner server name defined in Jenkins
     */
    String scannerServer = 'sonar-default-server'
}
