/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.arguments

import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.arguments.GenericSetupArguments

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSPipeline#setup(org.zowe.pipelines.nodejs.arguments.NodeJSSetupArguments)}
 * method.
 */
class NodeJSSetupArguments extends GenericSetupArguments {
    /**
     * Amount of time allowed to install dependencies.
     *
     * @default 5 Minutes
     */
    StageTimeout installDependencies = [time: 5, unit: TimeUnit.MINUTES]
}
