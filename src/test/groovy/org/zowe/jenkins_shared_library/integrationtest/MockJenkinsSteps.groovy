/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.integrationtest

import java.util.logging.Logger
import org.junit.*
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils
import static groovy.test.GroovyAssert.*

/**
 * Test {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline}
 */
class MockJenkinsSteps {
    /**
     * logger object to write logs
     */
    Logger logger

    MockJenkinsSteps() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())
    }

    void sh(script) {
        logger.info("[MockJenkinsSteps] sh ${script}")
    }

    void echo(message) {
        logger.info("[MockJenkinsSteps] echo ${message}")
    }
}
