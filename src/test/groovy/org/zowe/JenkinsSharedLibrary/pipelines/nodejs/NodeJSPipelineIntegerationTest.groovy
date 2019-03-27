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
import org.zowe.jenkins-shared-library.integrationtest.*

/**
 * Test {@link org.zowe.pipelines.nodejs.NodeJSPipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class NodeJsPipelineIntegerationTest {
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
        testJobName = "nodejs-example-${Utils.getTimestamp()}"
        fullTestJobName = [Constants.INTEGRATION_TEST_JENKINS_FOLDER, testJobName]
        logger.fine("Test job \"${fullTestJobName}\" will be created for testing")

        // create test job
        api.createJob(
            testJobName,
            'multibranchPipeline.xml',
            [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
            [
                'fvt-github-credential'     : System.getProperty('github.credential'),
                'fvt-git-owner'             : 'zowe',
                'fvt-git-repository'        : 'jenkins-library-fvt-nodejs',
                'fvt-jenkinsfile-path'      : 'Jenkinsfile',
            ]
        )

        // fetch parameters for master branch
        List job = fullTestJobName.collect()
        // will start building master
        job.add('master')
        api.fetchJobParameters(job)
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
        List job = fullTestJobName.collect()
        // will start building master
        job.add('master')
        def result = api.startJobAndGetResult(job, [
            'FETCH_PARAMETER_ONLY': 'false',
            'LIBRARY_BRANCH': System.getProperty('library.branch')
        ])

        assertEquals("SUCCESS", result)
    }
}
