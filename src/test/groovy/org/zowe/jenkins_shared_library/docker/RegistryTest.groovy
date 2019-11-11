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
class DockerRegistryTest extends IntegrationTest {
    @BeforeClass
    public static void setup() {
        def envVars = """GITHUB_USERNAME=${System.getProperty('github.username')}
GITHUB_EMAIL=${System.getProperty('github.email')}
GITHUB_CREDENTIAL=${System.getProperty('github.credential')}
DOCKER_REGISTRY=${System.getProperty('docker.registry')}
DOCKER_CREDENTIAL=${System.getProperty('docker.credential')}
DOCKER_IMAGE_REPFIX=${System.getProperty('docker.imageprefix')}
"""

        initPipelineJob([
            'name'      : 'docker-registry',
            'pipeline'  : 'dockerRegistryTest',
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
        assertThat('Build console log', buildLog, containsString('[DOCKER_REGISTRY_TEST] init successfully'))
    }

    @Test
    void testBuild() {
        assertThat('Build console log', buildLog, containsString('[DOCKER_REGISTRY_TEST] build successfully'))
    }

    @Test
    void testPublish() {
        assertThat('Build console log', buildLog, containsString('[DOCKER_REGISTRY_TEST] publish successfully'))
    }

    @Test
    void testPullRun() {
        // during test, local image is cleaned
        // so running the image should pull from remote
        assertThat('Build console log', buildLog, containsString('Unable to find image '))
        assertThat('Build console log', buildLog, containsString('[DOCKER_REGISTRY_TEST] pull and run successfully'))
    }
}
