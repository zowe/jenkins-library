/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library

import java.util.logging.Logger
import org.zowe.jenkins_shared_library.Utils

class TestX {
    /**
     * logger object to write logs
     */
    private transient Logger logger

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
     * def npm = new Registry(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    TestX(steps) {
        this.steps = steps
        setupTransients()
    }

    private void setupTransients() {
        if (!logger) {
            logger = Utils.getLogger(Class.getSimpleName())
        }
    }

    /**
     * Initialize github properties
     */
    void init(Map args = [:]) {
        setupTransients()
        logger.info("info - running init ...")
        this.steps.echo "echo - running init ..."
    }
}
