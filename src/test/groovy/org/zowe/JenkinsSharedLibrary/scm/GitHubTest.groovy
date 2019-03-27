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
import com.lesfurets.jenkins.unit.BasePipelineTest
import org.zowe.jenkins_shared_library.scm.GitHub

/**
 * Test {@link org.zowe.jenkins_shared_library.scm.GitHub}
 */
class GitHubTest extends BasePipelineTest {
    final static TEST_REPOSITORY = 'zowe/jenkins-library-fvt-nodejs'

    def github

    @Override
    @Before
    void setUp() throws Exception {
        super.setUp()

        binding.setVariable('GITHUB_USERNAME', System.getProperty('github.username'))
        binding.setVariable('GITHUB_EMAIL', System.getProperty('github.email'))
        binding.setVariable('GITHUB_CREDENTIAL', System.getProperty('github.credential'))
        // helper.registerAllowedMethod("sh", [Map.class], {c -> "bcc19744"})
        // helper.registerAllowedMethod("timeout", [Map.class, Closure.class], null)
        // helper.registerAllowedMethod("timestamps", [], { println 'Printing timestamp' })
        // helper.registerAllowedMethod(method("readFile", String.class), { file ->
        //     return Files.contentOf(new File(file), Charset.forName("UTF-8"))
        // })
    }

    // @Before
    // initGitHubInstance() {
    //     // github = new GitHub([
    //     //     'repository'                 : TEST_REPOSITORY,
    //     //     'username'                   : System.getProperty('github.username'),
    //     //     'email'                      : System.getProperty('github.email'),
    //     //     'usernamePasswordCredential' : System.getProperty('github.credential'),
    //     // ])
    // }

    @Test
    void testGitHubClone() {
        def script = loadScript("src/test/resources/pipelines/githubTest.groovy")
        println "${script}"
            script.execute()
            printCallStack()
        // github.clone()

        assertJobStatusSuccess()
    }
}
