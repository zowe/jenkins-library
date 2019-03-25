/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.exceptions

import org.zowe.pipelines.base.exceptions.StageException

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)} method.
 */
class BuildStageException extends StageException {
    /**
     * Create the exception.
     * @param message The exception message
     * @param stageName The name of the stage throwing the exception
     */
    BuildStageException(String message, String stageName) {
        super(message, stageName)
    }
}
