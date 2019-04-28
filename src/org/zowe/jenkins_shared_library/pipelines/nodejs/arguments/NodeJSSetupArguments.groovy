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
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline#setup(org.zowe.jenkins_shared_library.pipelines.nodejs.arguments.NodeJSSetupArguments)}
 * method.
 */
class NodeJSSetupArguments extends GenericSetupArguments {
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
     * Use configurations defined at {@link org.zowe.jenkins_shared_library.npm.Registry#init}.
     */
    List<Map> installRegistries

    /**
     * npm publish registry configurations
     *
     * Use configurations defined at {@link org.zowe.jenkins_shared_library.npm.Registry#init}.
     */
    Map publishRegistry

    /**
     * If we always use `npm install`.
     *
     * By default, with value false, the install dependencies stage will try to decide whether use `npm ci` or `npm install` based on existence of `package.json`.
     */
    Boolean alwaysUseNpmInstall = false

    /**
     * If exit the pipeline if the git folder is not clean after install dependencies.
     *
     * Usually the failure is caused by mismatched package-lock.json or wrong registry configuration.
     *
     * Pipeline will always exit if there are changes other than lock files.
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
