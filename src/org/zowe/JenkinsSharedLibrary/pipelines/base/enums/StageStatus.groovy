/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.base.enums

/**
 * Status levels available for a stage type.
 */
enum StageStatus {
    /**
     * The stage was created but hasn't executed yet.
     */
    CREATE,

    /**
     * The stage is currently executing.
     */
    EXECUTE,

    /**
     * The stage failed execution.
     */
    FAIL,

    /**
     * The stage was skipped
     */
    SKIP,

    /**
     * The stage successfully executed.
     */
    SUCCESS
}
