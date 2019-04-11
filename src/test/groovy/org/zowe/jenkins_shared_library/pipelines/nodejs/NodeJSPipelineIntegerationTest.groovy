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
 * Test {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
@Ignore
class NodeJsPipelineIntegerationTest extends IntegrationTest {
    // this github owner will be used for testing
    static final String TEST_OWNER = 'zowe'
    // this github repository will be used for testing
    static final String TEST_REPORSITORY = 'jenkins-library-fvt-nodejs'
    // branch to run test
    static final String TEST_BRANCH = 'master'

    @BeforeClass
    public static void setup() {
        initMultiBranchPipelineJob([
            'name'           : 'nodejs-multibranch',
            'git-credential' : System.getProperty('github.credential'),
            'git-owner'      : TEST_OWNER,
            'git-repository' : TEST_REPORSITORY,
            'branch'         : TEST_BRANCH,
        ])
    }

    @AfterClass
    public static void teardown() {
        // delete the test job if exists
        if (jenkins && testJobName &&
            buildInformation && buildInformation.containsKey('result') &&
            buildInformation['result'] == 'SUCCESS') {
            jenkins.deleteJob(fullTestJobName)
        }
    }

    @Test
    void testBuildInformation() {
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('number'));
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('result'));
        assertThat('Build result', buildInformation['result'], equalTo('SUCCESS'));
        assertThat('Build console log', buildLog, not(equalTo('')))
    }
}
