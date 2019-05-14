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
 * Test {@link org.zowe.jenkins_shared_library.npm.Registry}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with parameter pointing to the library branch to test
 */
class NpmRegistryTest extends IntegrationTest {
    @BeforeClass
    public static void setup() {
        def envVars = """GITHUB_USERNAME=${System.getProperty('github.username')}
GITHUB_EMAIL=${System.getProperty('github.email')}
GITHUB_CREDENTIAL=${System.getProperty('github.credential')}
NPM_USERNAME=${System.getProperty('npm.username')}
NPM_EMAIL=${System.getProperty('npm.email')}
NPM_CREDENTIAL=${System.getProperty('npm.credential')}
"""

        initPipelineJob([
            'name'      : 'npm-registry',
            'pipeline'  : 'npmRegistryTest',
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
    void testBuildInformation() {
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('number'));
        assertThat('Build result', buildInformation, IsMapContaining.hasKey('result'));
        assertThat('Build result', buildInformation['result'], equalTo('SUCCESS'));
        assertThat('Build console log', buildLog, not(equalTo('')))
    }

    @Test
    void testInit() {
        assertThat('Build console log', buildLog, containsString('[NPM_REGISTRY_TEST] init successfully'))
    }

    @Test
    void testCheckPackageInformation() {
        assertThat('Build console log', buildLog, containsString('[NPM_REGISTRY_TEST] check-info successfully'))
    }

    @Test
    void testLogin() {
        assertThat('Build console log', buildLog, containsString('[NPM_REGISTRY_TEST] login successfully'))
    }

    @Test
    void testPatch() {
        assertThat('Build console log', buildLog, containsString('[NPM_REGISTRY_TEST] patch successfully'))
    }
}
