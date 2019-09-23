/*
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
 * Test {@link org.zowe.jenkins_shared_library.package.Signing}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with parameter pointing to the library branch to test
 */
class PaxTest extends IntegrationTest {
    @BeforeClass
    public static void setup() {
        def envVars = """CODE_SIGNING_KEY_PASSPHRASE=${System.getProperty('signing.key.passphrase')}
CODE_SIGNING_PRIVATE_KEY_FILE=${System.getProperty('signing.key.file')}
"""

        initPipelineJob([
            'name'      : 'package-signing',
            'pipeline'  : 'paxPackageTest',
            'env-vars'  : envVars,
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
    void testInit() {
        assertThat('Build console log', buildLog, containsString('[PACKAGE_SIGNING_TEST] init successfully'))
    }

    @Test
    void testSign() {
        assertThat('Build console log', buildLog, containsString('[PACKAGE_SIGNING_TEST] sign successfully'))
    }

    @Test
    void testVerifySignature() {
        assertThat('Build console log', buildLog, containsString('[PACKAGE_SIGNING_TEST] verifySignature successfully'))
    }

    @Test
    void testHash() {
        assertThat('Build console log', buildLog, containsString('[PACKAGE_SIGNING_TEST] hash successfully'))
    }
}
