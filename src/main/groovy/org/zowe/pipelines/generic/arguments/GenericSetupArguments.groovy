/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.arguments

import org.zowe.pipelines.base.arguments.SetupArguments
import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the {@link org.zowe.pipelines.generic.GenericPipeline#setupGeneric(org.zowe.pipelines.generic.arguments.GenericSetupArguments)}
 * method.
 */
class GenericSetupArguments extends SetupArguments {
    /**
     * Amount of time allowed for the git setup.
     *
     * @default 1 Minute
     */
    StageTimeout gitSetup = [time: 1, unit: TimeUnit.MINUTES]

    /**
     * Amount of time allowed for the CI Skip check.
     *
     * @default 1 Minute
     */
    StageTimeout ciSkip = [time: 1, unit: TimeUnit.MINUTES]
}
