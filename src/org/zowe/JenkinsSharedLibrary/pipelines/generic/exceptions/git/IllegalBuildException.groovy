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

import org.zowe.pipelines.generic.enums.BuildType
import org.zowe.pipelines.generic.enums.GitOperation

/**
 * An exception that is thrown when git operations are executed on an unsupported build type.
 */
class IllegalBuildException extends GitException {
    /**
     * The expected git operation.
     */
    final GitOperation gitOperation

    /**
     * The current build type.
     */
    final BuildType buildType

    /**
     * Construct the exception.
     * @param gitOperation The expected git operation.
     * @param buildType The current build type.
     */
    IllegalBuildException(GitOperation gitOperation, BuildType buildType) {
        super("\"$gitOperation\" not supported for build \"$buildType\"")

        this.gitOperation = gitOperation
        this.buildType = buildType
    }
}
