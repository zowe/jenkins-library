/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.interfaces

/**
 * Required properties of a model used in the {@link org.zowe.pipelines.base.ProtectedBranches}
 * class.
 */
interface ProtectedBranchProperties {
    /**
     * The branch must have a name associated with it.
     * @return The name of the branch
     */
    String getName()
}