/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.scm

import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.exceptions.UnderConstructionException

class GitHub {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * github domain name
     */
    public static final String GITHUB_DOMAIN = 'github.com'

    /**
     * Default branch name
     */
    public static final String DEFAULT_BRANCH = 'master'

    /**
     * github user name
     */
    String username

    /**
     * github user email
     */
    String email

    /**
     * Jenkins credential ID for github username/password
     */
    String usernamePasswordCredential

    /**
     * Github repository in format of:
     * - org/repo-name
     * - user/repo-name
     */
    String repository

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def npm = new Registry(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    GitHub(steps) {
        this.steps = steps
    }

    /**
     * Initialize github properties
     * @param   repository                  repository name
     * @param   usernamePasswordCredential  github username/password credential
     * @param   username                    github username
     * @param   email                       github email
     */
    void init(Map args = [:]) {
        if (args['username']) {
            this.username = args['username']
            this.steps.sh "git config --global user.name \"${username}\""
        }
        if (args['email']) {
            this.email = args['email']
            this.steps.sh "git config --global user.email \"${email}\""
        }

        if (args['usernamePasswordCredential']) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']
        }

        if (args['repository']) {
            this.repository = args['repository']
        }
    }

    /**
     * Clone a repository
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param  shallow           if do a shallow
     */
    void cloneRepository(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!repository) {
            throw new InvalidArgumentException('repository')
        }

        def depthOpt = args['shallow'] ? ' --depth 1' : ''
        def branchOpt = args['branch'] ? " -b '${args['branch']}'" : ''
        def folderOpt = args['targetFolder'] ? " '${args['targetFolder']}'" : ''

        this.steps.sh "git clone ${depthOpt} 'https://${GITHUB_DOMAIN}/${repository}.git'${branchOpt}${folderOpt}"
    }

    /**
     * Commit changes
     */
    void commit(Map args = [:]) {
        throw new UnderConstructionException('GitHub.commit() method is not implemented yet.')
    }

    void push(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!repository) {
            throw new InvalidArgumentException('repository')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        withCredentials([usernamePassword(
            credentialsId: usernamePasswordCredential,
            passwordVariable: 'PASSWORD',
            usernameVariable: 'USERNAME'
        )]) {
            this.steps.sh "git push 'https://${USERNAME}:${PASSWORD}@${GITHUB_DOMAIN}/${repository}.git'"
        }
    }

    /**
     * Tag the branch
     */
    void tag(Map args = [:]) {
        throw new UnderConstructionException('GitHub.tag() method is not implemented yet.')
    }
}
