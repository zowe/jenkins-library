/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.exceptions.git

import org.zowe.pipelines.base.exceptions.PipelineException

/**
 * An exception that is thrown when an error occurs during a git operation.
 */
class GitException extends PipelineException {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    GitException(String message) {
        super(message)
    }
}
