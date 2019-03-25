/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.integrationtest

import org.zowe.pipelines.base.exceptions.StageException

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.integrationtest.JenkinsAPI} class.
 */
class JenkinsAPIException extends Exception {
    /**
     * Construct the exception.
     * @param message The exception message.
     */
    JenkinsAPIException(String message) {
        super(message)
    }
}
