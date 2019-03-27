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
import org.zowe.scm.GitHub

/**
 * Test {@link org.zowe.scm.GitHub}
 */
class GitHubTest {
    final static TEST_REPOSITORY = 'zowe/jenkins-library-fvt-nodejs'

    def github

    @Before
    initGitHubInstance() {
        github = new GitHub([
            'repository'                 : TEST_REPOSITORY,
            'username'                   : System.getProperty('github.username'),
            'email'                      : System.getProperty('github.email'),
            'usernamePasswordCredential' : System.getProperty('github.credential'),
        ])
    }

    @Test
    testGitHubClone() {
        github.clone()
    }
}
