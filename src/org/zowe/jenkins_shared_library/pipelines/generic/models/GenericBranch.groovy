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

import org.zowe.jenkins_shared_library.pipelines.base.models.Branch

/**
 * @see jenkins_shared_library.pipelines.base.models.Branch
 */
class GenericBranch extends Branch {
    /**
     * If this branch is allowed to release
     */
    Boolean allowRelease = false

    /**
     * If this branch is allowed to do a Formal release (like v1.2.3 without pre-release string)
     */
    Boolean allowFormalRelease = false

    /**
     * Branch tag when performing a release
     */
    String releaseTag
}
