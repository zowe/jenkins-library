/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.nodejs.arguments

import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.GenericSetupStageArguments

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline#setup(org.zowe.jenkins_shared_library.pipelines.nodejs.arguments.NodeJSSetupArguments)}
 * method.
 */
class NodeJSSetupStageArguments extends GenericSetupStageArguments {
    /**
     * Amount of time allowed to install dependencies.
     *
     * @default 5 Minutes
     */
    StageTimeout installDependencies = [time: 5, unit: TimeUnit.MINUTES]
    /**
     * Amount of time allowed to audit.
     *
     * @default 5 Minutes
     */
    StageTimeout audit = [time: 5, unit: TimeUnit.MINUTES]

    /**
     * Amount of time allowed to lint.
     *
     * @default 5 Minutes
     */
    StageTimeout lint = [time: 2, unit: TimeUnit.MINUTES]

    /**
     * npm install registry configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.npm.Registry#init(Map)}.</p>
     */
    List<Map> installRegistries

    /**
     * If we want to use another version of node.js.
     *
     * <p>This requires nvm installed on the build container.</p>
     */
    String nodeJsVersion

    /**
     * Path to nvm.sh.
     *
     * @default /home/jenkins/.nvm/nvm.sh
     */
    String nvmInitScript = '/home/jenkins/.nvm/nvm.sh'

    /**
     * npm publish registry configurations
     *
     * <p>Use configurations defined at {@link jenkins_shared_library.npm.Registry#init(Map)}.</p>
     */
    Map publishRegistry

    /**
     * If we always use {@code npm install}, never use {@code npm ci}.
     *
     * <p>By default, with value false, the install dependencies stage will try to decide whether
     * use {@code npm ci} or {@code npm install} based on existence of {@code package.json}.</p>
     */
    Boolean alwaysUseNpmInstall = false

    /**
     * If exit the pipeline if the git folder is not clean after install dependencies.
     *
     * <p>Usually the failure is caused by mismatched package-lock.json or wrong registry configuration.</p>
     *
     * <p>Pipeline will always exit if there are changes other than lock files.</p>
     */
    Boolean exitIfFolderNotClean = false

    /**
     * If continue the pipeline if npm audit failed.
     */
    Boolean ignoreAuditFailure = false

    /**
     * If we disable the default lint stage
     */
    Boolean disableLint = false

    /**
     * If we disable the default audit stage
     */
    Boolean disableAudit = false
}
