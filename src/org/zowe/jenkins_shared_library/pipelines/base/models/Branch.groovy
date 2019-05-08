/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.models

import org.zowe.jenkins_shared_library.pipelines.base.interfaces.BranchProperties
import org.zowe.jenkins_shared_library.pipelines.Constants

/**
 * Properties of a branch
 */
class Branch implements BranchProperties {
    /**
     * The string name of the branch
     */
    String name

    /**
     * If the branch is a protected branch.
     *
     * <p>A protected branch is usually one that has some restrictions on what code
     * can be published to it. These are typically your release and forward development
     * branches.</p>
     *
     * @Note If a branch is marked as protected, emails will always be sent out to the committers and
     * the list of {@link jenkins_shared_library.pipelines.base.Pipeline#admins} provided.
     */
    Boolean isProtected = false

    /**
     * Build history length.
     *
     * @Default For non-protected branches, default value is {@link jenkins_shared_library.pipelines.Constants#DEFAULT_BUILD_HISTORY}.
     * For protected branches, default value is {@link jenkins_shared_library.pipelines.Constants#DEFAULT_BUILD_HISTORY_FOR_PROTECTED_BRANCH}.
     */
    Integer buildHistory
}
