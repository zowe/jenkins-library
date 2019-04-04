/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.artifact

import org.zowe.jenkins_shared_library.Utils

interface ArtifactInterface {
    /**
     * Init artifactory configuration
     */
    void init(Map args = [:])

    /**
     * Get artifact information
     */
    Map getArtifact(Map args = [:])

    /**
     * Download artifacts
     */
    void download(Map args = [:])

    /**
     * Upload an artifact
     */
    void upload(Map args = [:])

    /**
     * Search artifacts with pattern
     */
    void search(Map args = [:])

    /**
     * Promote artifact
     */
    void promote(Map args = [:])
}
