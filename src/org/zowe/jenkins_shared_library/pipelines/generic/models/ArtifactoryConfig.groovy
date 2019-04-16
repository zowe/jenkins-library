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

/**
 * Git configuration used to checkout the source.
 */
class ArtifactoryConfig {
    /**
     * The artifactory url
     */
    String url

    /**
     * The credentials id in Jenkins
     */
    String credentialsId
}
