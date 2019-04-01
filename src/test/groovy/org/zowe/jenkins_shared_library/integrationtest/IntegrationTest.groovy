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
import static groovy.test.GroovyAssert.*

/**
 * Test {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class IntegrationTest {
    /**
     * JenkinsAPI instance
     */
    protected JenkinsAPI api
    /**
     * Logger object
     */
    protected Logger logger

    /**
     * The test job name. After test is done, the job should be deleted.
     */
    protected String testJobName

    /**
     * Full test job name including parent folders
     */
    protected List<String> fullTestJobName

    /**
     * Build result
     */
    protected Map buildResult

    /**
     * Build console log
     */
    protected String buildLog

    /**
     * Init test jon name
     * @param  namePrefix        prefix of the job name
     */
    void _initTestJobName(String namePrefix = 'test') {
            // init test job name
        testJobName = "${namePrefix}-${Utils.getTimestamp()}"
        fullTestJobName = [Constants.INTEGRATION_TEST_JENKINS_FOLDER, testJobName]
        logger.fine("Test job \"${fullTestJobName}\" will be created for testing")
    }

    @BeforeAll
    void initTestJob() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())

        // init JenkinsAPI
        api = JenkinsAPI.init()
    }
}
