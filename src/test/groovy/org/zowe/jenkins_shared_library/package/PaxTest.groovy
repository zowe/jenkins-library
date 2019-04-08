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
 * Test {@link org.zowe.jenkins_shared_library.package.Pax}
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
        def envVars = """PAX_SERVER_HOST=${System.getProperty('pax.server.host')}
PAX_SERVER_PORT=${System.getProperty('pax.server.port')}
PAX_SERVER_CREDENTIAL=${System.getProperty('pax.server.crdential')}
"""

        initPipelineJob([
            'name'      : 'pax-package',
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
        assertThat('Build console log', buildLog, containsString('[PAX_PACKAGE_TEST] init successfully'))
    }

    @Test
    void testPackage() {
        assertThat('Build console log', buildLog, containsString('[PAX_PACKAGE_TEST] package successfully'))
    }
}
