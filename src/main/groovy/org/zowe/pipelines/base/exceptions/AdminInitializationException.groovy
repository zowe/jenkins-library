/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.exceptions

/**
 * An exception that is thrown when an issue has occurred while creating a
 * {@link org.zowe.pipelines.base.models.PipelineAdmin}.
 */
class AdminInitializationException extends PipelineException {
    /**
     * The User ID that caused the exception.
     */
    final String userId

    /**
     * Construct the exception.
     * @param message The exception message.
     * @param userId The User ID that caused the exception.
     */
    AdminInitializationException(String message, String userId) {
        super("$message (userId = $userId")
        this.userId = userId
    }
}
