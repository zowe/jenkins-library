/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.exceptions

/**
 * A generic exception that can be thrown during any stage of your Jenkins pipeline.
 */
class StageException extends PipelineException {
    /**
     * The stage where the exception was thrown.
     */
    final String stageName

    /**
     * Construct the exception.
     * @param message The exception message.
     * @param stageName The name of the stage that threw the exception.
     */
    StageException(String message, String stageName) {
        super("${message} (stage = \"${stageName}\")")

        this.stageName = stageName
    }
}
