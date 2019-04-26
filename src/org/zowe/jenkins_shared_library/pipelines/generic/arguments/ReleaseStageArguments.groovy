/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic.arguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#releaseGeneric(java.util.Map)} method.
 */
class ReleaseStageArguments extends GenericStageArguments {
    /**
     * The name of the Releasing step.
     *
     * @default {@code "Package"}
     */
    String name = "Package"

    /**
     * Custom script of how to tag branch
     */
    Closure tagBranch

    /**
     * Custom script of how to bump version
     */
    Closure bumpVersion
}
