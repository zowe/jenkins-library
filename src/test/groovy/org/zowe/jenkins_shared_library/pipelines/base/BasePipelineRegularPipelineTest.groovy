/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

import java.util.logging.Logger
import org.junit.*
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.collection.IsMapContaining
import org.zowe.jenkins_shared_library.integrationtest.*
import static groovy.test.GroovyAssert.*

/**
 * Test {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class BasePipelineMultibranchPipelineTest extends IntegrationTest {
    // this github owner will be used for testing
    static final String TEST_OWNER = 'zowe'
    // this github repository will be used for testing
    static final String TEST_URL = 'https://github.com/zowe/jenkins-library-fvt-nodejs.git'
    // branch to run test
    static final String TEST_BRANCH = 'master'
    // branch to run test
    static final String TEST_JENKINSFILE = 'Jenkinsfile.base'

    @BeforeClass
    public static void setup() {
        // init the job and fetch parameters
        initPipelineJob([
            'name'             : 'base-regular',
            'git-credential'   : System.getProperty('github.credential'),
            'git-url'          : TEST_URL,
            'git-branch'       : TEST_BRANCH,
            'jenkinsfile-path' : TEST_JENKINSFILE
        ])

        // start the job, wait for it's done and get build result
        buildInformation = jenkins.startJobAndGetBuildInformation(job, [
            'FETCH_PARAMETER_ONLY' : 'false',
            'LIBRARY_BRANCH'       : System.getProperty('library.branch')
        ])

        // load job console log
        if (buildInformation && buildInformation['number']) {
            buildLog = jenkins.getBuildLog(job, buildInformation['number'])
        }
    }

    @AfterClass
    public static void teardown() {
        // delete the test job if exists
        if (jenkins && testJobName &&
            buildInformation && buildInformation.containsKey('result') &&
            buildInformation['result'] == 'SUCCESS') {
            // jenkins.deleteJob(fullTestJobName)
        }
    }

    @Test
    void testBuildInformation() {
        // buildInformation
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('number'));
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('result'));
        assertThat('Build result', buildInformation['result'], equalTo('SUCCESS'));
    }

    @Test
    void testConsoleLog() {
        // console log
        assertThat('Build console log', buildLog, not(equalTo('')))
        // custom stage is configured and started
        assertThat('Build console log', buildLog, containsString('This step can be skipped by setting the `Skip Stage: CustomStage` option to true'))
        assertThat('Build console log', buildLog, containsString('This is a custom stage, skippable'))
        // complete stage should be started
        assertThat('Build console log', buildLog, containsString('Pipeline Execution Complete'))
    }
}
