/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.models

/**
 * State of the change information for a given git commit
 */
class ChangeInformation {
    /**
     * Is this change part of a pull request.
     */
    final boolean isPullRequest

    /**
     * The branch name reported by the build.
     */
    final String branchName

    /**
     * The base branch for a pull request.
     *
     * <p>This property will be null if {@link #isPullRequest} is false.</p>
     */
    final String baseBranch

    /**
     * The change branch for a pull request.
     *
     * <p>This property will be null if {@link #isPullRequest} is false.</p>
     */
    final String changeBranch

    /**
     * Construct the class.
     * @param steps This is the workflow context that is used to determine the change status.
     */
    ChangeInformation(steps) {
        branchName = steps.BRANCH_NAME

        if (steps.env.CHANGE_BRANCH) {
            isPullRequest = true
            baseBranch = steps.CHANGE_TARGET
            changeBranch = steps.CHANGE_BRANCH
        } else {
            isPullRequest = false
            baseBranch = null
            changeBranch = null
        }
    }
}
