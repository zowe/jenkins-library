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
 * Test {@link org.zowe.jenkins_shared_library.pipelines.generic.Pipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class GenericPipelineMultibranchPipelineTest extends IntegrationTest {
    // this github owner will be used for testing
    static final String TEST_OWNER = 'zowe'
    // this github repository will be used for testing
    static final String TEST_REPORSITORY = 'jenkins-library-fvt-nodejs'
    // branch to run test
    static final String TEST_BRANCH = 'master'
    // branch to run test
    static final String TEST_JENKINSFILE = 'Jenkinsfile.generic-multibranch'

    @BeforeClass
    public static void setup() {
        initMultiBranchPipelineJob([
            'name'             : 'generic-multibranch',
            'git-credential'   : System.getProperty('github.credential'),
            'git-owner'        : TEST_OWNER,
            'git-repository'   : TEST_REPORSITORY,
            'branch'           : TEST_BRANCH,
            'jenkinsfile-path' : TEST_JENKINSFILE
        ])
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
        [
            // build stage
            'Executing stage Build: Source',
            '+ npm install',
            '+ npm run build',
            // custom stage is configured and started
            'This step can be skipped by setting the `Skip Stage: CustomStage` option to true',
            'This is a custom stage, skippable',
            // test stage
            'Executing stage Test: Unit',
            '+ npm run test:unit',
            'Recording test results', // junit
            '[Cobertura] Publishing Cobertura coverage report...', // cobertura
            '[htmlpublisher] Archiving HTML reports...',
            // complete stage
            'Pipeline Execution Complete',
            // sending email
            'Sending email notification...',
            "Subject: [${buildInformation['result']}] Job \'${fullTestJobName.join('/')}/${TEST_BRANCH} [${buildInformation['number']}]\'"
        ].each {
            assertThat('Build console log', buildLog, containsString(it))
        }
    }
}
