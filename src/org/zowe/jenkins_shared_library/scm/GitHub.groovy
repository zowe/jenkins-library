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

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.exceptions.UnderConstructionException
import org.zowe.jenkins_shared_library.Utils

@Log
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
     * github domain name for downloading user content
     */
    public static final String GITHUB_DOWNLOAD_DOMAIN = 'raw.githubusercontent.com'

    /**
     * github domain name for api calls
     */
    public static final String GITHUB_API_DOMAIN = 'api.github.com'

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
     * @param   username                    github user.name config. Optional, can be extracted from credential
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
                this.username = "\${USERNAME}"
                this.steps.echo "Git username (\${USERNAME}) is set to: ${this.username}"
                // FIXME: encode username/passsword?
                this.steps.sh "echo \"https://\${USERNAME}:\${PASSWORD}@${GITHUB_DOMAIN}\" > ~/.git-credentials"
            }
        }

        // can be extracted from credential, so this is optional
        if (args['username']) {
            this.username = args['username']
        }

        // run git config with configurations we have
        this.config()
    }

    /**
     * We can guess repository/branch information from an existing git folder
     *
     * @param folder          which folder to test
     */
    void initFromFolder(String folder = '') {
        if (!folder) {
            folder = './'
        }

        // work in target folder
        this.steps.dir(folder) {
            // is this a git folder?
            if (this.steps.fileExists('.git')) {
                // try to guess repository
                if (!this.repository) {
                    log.fine('try to init repository')
                    def repo = this.steps.sh(script: 'git remote -v | grep origin | grep fetch', returnStdout: true).trim()
                    log.fine("found remote ${repo}")
                    // origin   https://github.com/zowe/zowe-install-packaging.git (fetch)
                    // origin   git@github.com:zowe/zowe-install-packaging.git (fetch)
                    def mt = repo =~ /origin\s+(https:\/\/|git@)${GITHUB_DOMAIN}(\/|:)(.+)\.git\s+\(fetch\)/
                    if (mt && mt[0] && mt[0][3]) {
                        log.fine("use repository ${mt[0][3]}")
                        this.repository = mt[0][3]
                    } else {
                        log.fine("failed to extract repository")
                    }
                }

                // try to guess branch name
                if (!this.branch) {
                    if (this.steps.env && this.steps.env.CHANGE_BRANCH) {
                        // this is a PR and CHANGE_BRANCH is the original branch name
                        this.branch = this.steps.env.CHANGE_BRANCH
                    } else if (this.steps.env && this.steps.env.BRANCH_NAME) {
                        // this is multibranch pipeline and BRANCH_NAME is the branch name
                        this.branch = this.steps.env.BRANCH_NAME
                    } else {
                        // we try to use git command
                        def current = this.steps.sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                        if (current == 'HEAD') {
                            // detached mode, get commit id
                            current = this.steps.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                        }
                        this.branch = current
                    }
                }
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

        // run git config with configurations we have
        this.config()
    }

    /**
     * Setup git config based on properties
     */
    void config() {
        // work in target folder
        this.steps.dir(this.folder ?: './') {
            // is this a git folder?
            if (this.steps.fileExists('.git')) {
                // git configs for the repository
                if (this.username) {
                    this.steps.sh "git config user.name \"${this.username}\""
                }
                if (this.email) {
                    this.steps.sh "git config user.email \"${this.email}\""
                }
                // using https repository, indicate git push to check ~/.git-credentials
                this.steps.sh 'git config credential.helper store'
            }
        }
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
        // validate arguments
        if (!this.repository) {
            throw new InvalidArgumentException('repository')
        }
        if (!this.usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        def message = args['message'] ? args['message'] : ''
        // validate arguments
        if (!message) {
            throw new InvalidArgumentException('message')
        }

        // using https repository, indicate git push to check ~/.git-credentials
        this.command('git config credential.helper store')

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
        def res = status == ''

        if (res) {
            this.steps.echo "Working directory is clean."
        } else {
            this.steps.echo "Working directory is not clean:\n${status}"
        }

        return res
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

        def res = localHash == remoteHash

        if (res) {
            this.steps.echo "Working directory is synced with remote."
        } else {
            this.steps.echo "Working directory is not synced with remote:\n" +
                "local : ${localHash}\n" +
                "remote: ${remoteHash}"
        }

        return res
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

        // using https repository, indicate git push to check ~/.git-credentials
        this.command('git config credential.helper store')

        this.command("git tag \"${args['tag']}\" && git push origin \"${args['tag']}\"")
    }
}
