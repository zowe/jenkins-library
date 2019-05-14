/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic.models

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
     *
     * <p>If it's a pull request, it will be PR-&lt;id&gt;.</p>
     */
    final String branchName

    /**
     * The base branch for a pull request.
     *
     * <p>If the PR is merging branch "test" into "master", this value will be "master".</p>
     *
     * <p>This property will be null if {@link #isPullRequest} is false.</p>
     */
    final String baseBranch

    /**
     * The change branch for a pull request.
     *
     * <p>If the PR is merging branch "test" into "master", this value will be "test".</p>
     *
     * <p>This property will be null if {@link #isPullRequest} is false.</p>
     */
    final String changeBranch

    /**
     * Construct the class.
     * @param steps This is the workflow context that is used to determine the change status.
     */
    ChangeInformation(steps) {
        branchName = steps.env.BRANCH_NAME

        if (steps.env.CHANGE_BRANCH) {
            isPullRequest = true
            baseBranch = steps.env.CHANGE_TARGET
            changeBranch = steps.env.CHANGE_BRANCH
        } else {
            isPullRequest = false
            baseBranch = null
            changeBranch = null
        }
    }
}
