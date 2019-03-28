/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

import org.junit.*
import static groovy.test.GroovyAssert.*
import java.util.logging.Logger
import org.zowe.jenkins_shared_library.integrationtest.*

/**
 * Test {@link org.zowe.jenkins_shared_library.scm.GitHub}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class GitHubTest {
    /**
     * JenkinsAPI instance
     */
    JenkinsAPI api
    /**
     * Logger object
     */
    Logger logger

    /**
     * The test job name. After test is done, the job should be deleted.
     */
    String testJobName

    /**
     * Full test job name including parent folders
     */
    List<String> fullTestJobName

    @Before
    void initTestJob() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())

        // init JenkinsAPI
        api = JenkinsAPI.init()

        // init test job name
        testJobName = "test-github-${Utils.getTimestamp()}"
        fullTestJobName = [Constants.INTEGRATION_TEST_JENKINS_FOLDER, testJobName]
        logger.fine("Test job \"${fullTestJobName}\" will be created for testing")

        // create test job
        def envVars = """GITHUB_USERNAME='${System.getProperty('github.username')}'
GITHUB_EMAIL='${System.getProperty('github.email')}'
GITHUB_CREDENTIAL='${System.getProperty('github.credential')}'"""
        def script = Utils.loadResource('/pipelines/githubTest.groovy')
        api.createJob(
            testJobName,
            'pipeline.xml',
            [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
            [
                'fvt-env-vars'     : Utils.escapeXml(envVars),
                'fvt-script'       : Utils.escapeXml(script),
            ]
        )
    }

    @After
    void deleteTestJob() {
        // delete the test job if exists
        if (api && testJobName) {
            // api.deleteJob(fullTestJobName)
        }
    }

    @Test
    void testDefaultBuild() {
        def result = api.startJobAndGetResult(fullTestJobName, [
            'LIBRARY_BRANCH': System.getProperty('library.branch')
        ])

        assertEquals("SUCCESS", result)
    }
}
