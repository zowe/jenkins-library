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

/**
 * The semantic versioning level that can be changed within a branch.
 */
enum SemverLevel {
    /**
     * Allow a <b>MAJOR</b> or lower semantic versioning increase.
     */
    MAJOR,

    /**
     * Allow a <b>MINOR</b> or lower semantic versioning increase.
     */
    MINOR,

    /**
     * Allow a <b>PATCH</b> or lower semantic versioning increase.
     */
    PATCH
}