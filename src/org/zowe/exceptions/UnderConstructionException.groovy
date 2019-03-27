/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.exceptions

/**
 * An exception that can be thrown if the class method is not implemented
 */
class UnderConstructionException extends Exception {
    /**
     * Construct the exception.
     *
     * @param message    The exception message.
     */
    UnderConstructionException(String message = '') {
        super(message)

    }
}
