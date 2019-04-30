/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.gradle.models

import org.zowe.jenkins_shared_library.pipelines.generic.models.GenericBranch

/**
 * @see org.zowe.jenkins_shared_library.pipelines.generic.models.GenericBranch
 */
class GradleBranch extends GenericBranch {
    /**
     * This is the npm tag in which the branch will be published with. If this
     * property is left null, then the branch will not be published.
     */
    String npmTag
}

