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
    StageTimeout timeout = [time: 1, unit: TimeUnit.HOURS]

    /**
     * The name of the SonarQube Scan step.
     *
     * @default {@code (empty)}
     */
    String name = ""

    /**
     * The file name of the SonarQube project.properties file.
     *
     * @default {@code "sonar-project.properties"}
     */
    String sonarProjectFile = 'sonar-project.properties'

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

    /**
     * Customize JAVA_HOME for sonar scan if the default JDK is not supported
     *
     * @Note This default value is where we put JDK v11 in zowe-jenkins-agent
     *       build image. Pipelines are not using this base image should
     *       customize this value to match their environment.
     *
     * @default {@code /usr/java/openjdk-11}
     */
    String javaHome = '/usr/java/openjdk-11'

    /**
     * If the SonarQube server support branch scan.
     *
     * @Note If enable branch scan on a SonarQube server which doesn't support,
     *       you may receive failure build with error "To use the property
     *       "sonar.branch.name", the branch plugin is required but not
    *        installed. See the documentation of branch support:
    *        https://redirect.sonarsource.com/doc/branches.html."
     *
     * @default {@code false}
     */
    Boolean allowBranchScan = false

    /**
     * Fail build on quality gate failure
     *
     * @default {@code false}
     */
    Boolean failBuild = false
}
