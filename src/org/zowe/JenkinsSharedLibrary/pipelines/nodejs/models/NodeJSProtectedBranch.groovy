/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.models

import org.zowe.pipelines.base.models.ProtectedBranch

/**
 * @see org.zowe.pipelines.base.models.ProtectedBranch
 */
class NodeJSProtectedBranch extends ProtectedBranch {
    /**
     * Does the publish require approval?
     *
     * <p>If this is true, then a protected branch will be automatically published under the default
     * version.</p>
     */
    boolean autoDeploy = false

    /**
     * A map of dependencies and their required installable tags for this
     * protected branch.
     *
     * <p>If dependencies are specified, they will automatically be updated and kept in line
     * with the latest versions in the protected branch. For pull requests into a protected branch,
     * the dependencies required by the protected branch will be installed but not committed.</p>
     */
    Map<String, String> dependencies = [:]

    /**
     * A map of devDependencies and their required installable tags for this
     * protected branch.
     *
     * <p>If devDependencies are specified, they will automatically be updated and kept in line
     * with the latest versions in the protected branch. For pull requests into a protected branch,
     * the devDependencies required by the protected branch will be installed but not committed.</p>
     */
    Map<String, String> devDependencies = [:]

    /**
     * The max release level that will be prompted for the branch.
     *
     * @Note This does not restrict the level that can be manually committed. It only
     * restricts what will be asked in the version request.
     *
     * @default {@link SemverLevel#MAJOR}
     */
    SemverLevel level = SemverLevel.MAJOR

    /**
     * The prerelease string to apply between the semver and build date.
     *
     * <p>If this is omitted, the build will never ask to deploy a prerelease. If specified, the
     * build will always ask to deploy a prerelease. Committing a manual version to the branch can
     * override this specification</p>
     */
    String prerelease

    /**
     * This is the npm tag in which the branch will be published with. If this
     * property is left null, then the branch will not be published.
     */
    String tag
}

