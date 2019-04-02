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
     * Default remote name
     */
    public static final String DEFAULT_REMOTE = 'origin'

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
     * Github branch to checkout
     */
    String branch

    /**
     * Folder where the repository is cloned to
     */
    String folder

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
     * @param   branch                      branch name to clone/push
     * @param   folder                      target folder
     * @param   usernamePasswordCredential  github username/password credential
     * @param   username                    github user.name config
     * @param   email                       github user.email config
     */
    void init(Map args = [:]) {
        if (args['repository']) {
            this.repository = args['repository']
        }
        if (args['branch']) {
            this.branch = args['branch']
        } else {
            this.branch = DEFAULT_BRANCH
        }

        if (args['folder']) {
            this.folder = args['folder']
        }

        if (args['username']) {
            this.username = args['username']
        }
        if (args['email']) {
            this.email = args['email']
        }

        if (args['usernamePasswordCredential']) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']

            // write ~/.git-credentials to provide credential for push
            this.steps.withCredentials([this.steps.usernamePassword(
                credentialsId: this.usernamePasswordCredential,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )]) {
                // FIXME: encode username/passsword?
                this.steps.sh "echo \"https://\${USERNAME}:\${PASSWORD}@${GITHUB_DOMAIN}\" > ~/.git-credentials"
            }
        }
    }

    /**
     * Clone a repository
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param  branch        branch to checkout
     * @param  folder        which folder to save the cloned files
     */
    void cloneRepository(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.repository) {
            throw new InvalidArgumentException('repository')
        }
        if (!this.usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        if (this.folder) {
            this.steps.dir(this.folder) {
                this.steps.git(
                    url           : "https://${GITHUB_DOMAIN}/${this.repository}.git",
                    credentialsId : this.usernamePasswordCredential,
                    branch        : this.branch,
                    changelog     : false,
                    poll          : false
                )
            }
        } else {
            this.steps.git(
                url           : "https://${GITHUB_DOMAIN}/${this.repository}.git",
                credentialsId : this.usernamePasswordCredential,
                branch        : this.branch,
                changelog     : false,
                poll          : false
            )
        }

        // git configs for the repository
        if (this.username) {
            this.command("git config user.name \"${this.username}\"")
        }
        if (this.email) {
            this.command("git config user.email \"${this.email}\"")
        }
        // using https repository, indicate git push to check ~/.git-credentials
        this.command('git config credential.helper store')
}

    /**
     * Issue git command and get stdout return
     * @param  command     git command
     * @return             stdout log
     */
    String command(String command) {
        if (this.folder) {
            command = "cd '${this.folder}' && ${command}"
        }

        return this.steps.sh(script: command, returnStdout: true).trim()
    }

    /**
     * Commit changes
     * @param  message     git commit message
     */
    void commit(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        def message = args['message'] ? args['message'] : ''
        // validate arguments
        if (!message) {
            throw new InvalidArgumentException('message')
        }

        this.command("git add . && git commit -m '${message}'")
    }

    /**
     * Commit changes
     * @param  message     git commit message
     */
    void commit(String message) {
        this.commit(['message': message])
    }

    /**
     * Get last commit information
     *
     * @param fields    what information of the commit should be returned. Available fields are:
     *                  - hash
     *                  - shortHash
     *                  - author
     *                  - email
     *                  - timestamp
     *                  - date
     *                  - subject
     *                  - body
     * @return          last commit information map with fields asked for
     */
    Map getLastCommit(List fields = ['hash']) {
        Map result = [:]

        Map formats = [
            'hash'      : '%H',
            'shortHash' : '%h',
            'author'    : '%an',
            'email'     : '%ae',
            'timestamp' : '%at',
            'date'      : '%aD',
            'subject'   : '%s',
            'body'      : '%b',
        ]
        formats.each { entry ->
            if (fields.contains(entry.key)) {
                result[entry.key] = this.command("git show --format=\"${entry.value}\" -s HEAD")
            }
        }

        return result
    }

    /**
     * Push all local changes
     */
    void push(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        this.command("git push origin ${this.branch}")
    }

    /**
     * Check if current working tree is clean or not
     * @return         boolean to tell if it's clean or not
     */
    Boolean isClean(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        String status = this.command('git status --porcelain')
        return status == ''
    }

    /**
     * Check if current branch is synced with remote
     * @return         boolean to tell if it's synced or not
     */
    Boolean isSynced(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        String remote = args['remote'] ? args['remote'] : DEFAULT_REMOTE
        // update remote
        this.command("git fetch ${remote}")
        // get last hash
        String localHash = this.command("git rev-parse ${this.branch}")
        String remoteHash = this.command("git rev-parse ${remote}/${this.branch}")

        return localHash == remoteHash
    }

    /**
     * Reset current branch to origin
     *
     * Use similar parameters like init() method and with these extra:
     */
    void reset(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        String remote = args['remote'] ? args['remote'] : DEFAULT_REMOTE
        this.command("git fetch \"${remote}\" && git reset --hard ${remote}/${this.branch}")
    }

    /**
     * Tag the branch.
     *
     * Note: currently only support lightweighted tag.
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param  tag           tag name to be created
     */
    void tag(Map args = [:]) {
        this.command("git tag \"${args['tag']}\" && git push origin \"${args['tag']}\"")
    }
}
