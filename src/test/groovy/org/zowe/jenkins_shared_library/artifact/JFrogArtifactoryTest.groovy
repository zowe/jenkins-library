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
 * Test {@link org.zowe.jenkins_shared_library.artifact.JFrogArtifactory}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with parameter pointing to the library branch to test
 */
class JFrogArtifactoryTest extends IntegrationTest {
    @BeforeClass
    public static void setup() {
        def envVars = """ARTIFACTORY_URL=${System.getProperty('artifactory.url')}
ARTIFACTORY_CREDENTIAL=${System.getProperty('artifactory.credential')}
"""

        initPipelineJob([
            'name'      : 'jfrog-artifactory',
            'pipeline'  : 'jfrogArtifactoryTest',
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
        assertThat('Build console log', buildLog, containsString('[JFROG_ARTIFACTORY_TEST] init successfully'))
    }

    @Test
    void testGetArtifact() {
        assertThat('Build console log', buildLog, containsString('[JFROG_ARTIFACTORY_TEST] getArtifact successfully'))
    }

    @Test
    void testDownload() {
        assertThat('Build console log', buildLog, containsString('[JFROG_ARTIFACTORY_TEST] download successfully'))
    }

    @Test
    void testUpload() {
        assertThat('Build console log', buildLog, containsString('[JFROG_ARTIFACTORY_TEST] upload successfully'))
    }

    @Test
    void testPromote() {
        assertThat('Build console log', buildLog, containsString('[JFROG_ARTIFACTORY_TEST] promote successfully'))
    }
}
