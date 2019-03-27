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

import org.zowe.jenkins_shared_library.pipelines.generic.arguments.GenericStageArguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline#deploy(java.util.Map, java.util.Map)} method.
 */
class DeployStageArguments extends GenericStageArguments {
    /**
     * The name of the Deploy step.
     *
     * @default {@code "Package"}
     */
    String name = "Package"

    /**
     * The custom login operation.
     *
     * <p>This closure is used by the deploy stage method to perform any required login operations.
     * Additional documentation for this argument will be provided in each command.</p>
     */
    Closure customLogin
}
