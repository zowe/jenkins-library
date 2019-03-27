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

import org.zowe.pipelines.base.arguments.StageArguments

/**
 * Arguments available to stage creation methods present in {@link org.zowe.pipelines.generic.GenericPipeline}
 */
class GenericStageArguments extends StageArguments{
    /**
     * The stage operation.
     *
     * <p>This closure is used by the various stage methods to perform an operation for the
     * stage in conjunction with some common pre and post operation steps. Additional documentation
     * for this argument will be provided in each command.</p>
     */
    Closure operation
}
