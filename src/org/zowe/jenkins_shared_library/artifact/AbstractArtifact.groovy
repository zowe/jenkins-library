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

abstract class AbstractArtifact {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def o = new AbstractArtifact(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    AbstractArtifact(steps) {
        this.steps = steps
    }

    /**
     * Init artifactory configuration
     */
    abstract void init(Map args = [:])

    /**
     * Get artifact information
     */
    abstract Map getArtifact(Map args = [:])

    /**
     * Download artifacts
     */
    abstract void download(Map args = [:])

    /**
     * Upload an artifact
     */
    abstract void upload(Map args = [:])

    /**
     * Search artifacts with pattern
     */
    abstract void search(Map args = [:])

    /**
     * Promote artifact
     */
    abstract void promote(Map args = [:])
}
