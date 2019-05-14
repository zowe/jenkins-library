/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.artifact

/**
 * Interface for classes handling artifacts.
 *
 * <p>For example, JFrog Artifactory class should implement this interface. If we support Nexus,
 * the class should also implement this interface.</p>
 */
interface ArtifactInterface {
    /**
     * Init artifactory configuration
     */
    void init(Map args)

    /**
     * Get artifact information
     */
    Map getArtifact(Map args)

    /**
     * Download artifacts
     */
    void download(Map args)

    /**
     * Upload an artifact
     */
    void upload(Map args)

    /**
     * Promote artifact
     *
     * @return                   full path to the promoted artifact
     */
    String promote(Map args)
}
