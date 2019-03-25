/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.models

import org.zowe.pipelines.base.interfaces.ProtectedBranchProperties

/**
 * Properties of a protected branch
 *
 * <p>A protected branch is usually one that has some restrictions on what code
 * can be published to it. These are typically your release and forward development
 * branches.</p>
 *
 * <p>If a branch is marked as protected, emails will always be sent out to the committers and
 * the list of {@link org.zowe.pipelines.base.Pipeline#admins} provided.</p>
 */
class ProtectedBranch implements ProtectedBranchProperties {
    /**
     * The string name of the branch
     */
    String name
}
