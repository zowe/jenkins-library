/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic.exceptions

import org.zowe.jenkins_shared_library.pipelines.base.exceptions.StageException

/**
 * A generic exception that is thrown from within the
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#publishGeneric(java.util.Map)} method.
 */
class PublishStageException extends StageException {
    /**
     * Create the exception.
     * @param message The exception message
     * @param stageName The name of the stage throwing the exception
     */
    PublishStageException(String message, String stageName) {
        super(message, stageName)
    }
}
