/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.pipelines.generic.arguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#publishGeneric(java.util.Map)} method.
 */
class PublishStageArguments extends GenericStageArguments {
    /**
     * The name of the publishing step.
     *
     * @default {@code "Package"}
     */
    String name = "Package"

    /**
     * Artifacts to publish
     */
    List artifacts = []

    /**
     * Publishing target path
     */
    String publishTargetPath

    /**
     * By default, if you release a pre-release version on formal release branch, the pipeline will
     * show a confirmation information requiring human intervene.
     *
     * Set this to `true` to bypass the confirmation.
     */
    Boolean allowPublishPreReleaseFromFormalReleaseBranch = false
}
