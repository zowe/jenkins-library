/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.enums

/**
 * The available build types.
 */
enum BuildType {
    /**
     * A standard build.
     *
     * <p>This would be a build of a branch with no pull requests involved.</p>
     */
    STANDARD,

    /**
     * A pull request build.
     */
    PULL_REQUEST
}
