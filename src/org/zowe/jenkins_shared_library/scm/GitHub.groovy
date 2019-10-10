/*
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

/**
 * Methods to work with GitHub.
 *
 * @Example
 * <pre>
 *     def github = new GitHub(this)
 *     // init github configurations
 *     github.init(
 *         repository                 : 'zowe/explorer-jes',
 *         branch                     : 'master',
 *         usernamePasswordCredential : 'my-github-credential',
 *         email                      : 'my-github-user@gmail.com'
 *     )
 *     // do some modifications
 *     // ...
 *     // commit changes
 *     github.commit('My work is done')
 *     // push to remote
 *     github.push()
 *     // check if the branch is synced
 *     if (!github.isSynced()) {
 *         echo "Branch is not synced with remote."
 *     }
 * </pre>
 */
@Log
class GitHub {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * GitHub domain name.
     *
     * @Default {@code "github.com"}
     */
    public static final String GITHUB_DOMAIN = 'github.com'

    /**
     * GitHub domain name for downloading user content.
     *
     * @Default {@code "raw.githubusercontent.com"}
     */
    public static final String GITHUB_DOWNLOAD_DOMAIN = 'raw.githubusercontent.com'

    /**
     * GitHub domain name for api calls.
     *
     * @Default {@code "api.github.com"}
     */
    public static final String GITHUB_API_DOMAIN = 'api.github.com'

    /**
     * Default branch name.
     *
     * @Default {@code "master"}
     */
    public static final String DEFAULT_BRANCH = 'master'

    /**
     * Default remote name.
     *
     * @Default {@code "origin"}
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
     * Github repository in format of {@code owner/repo}. The owner can be organization or GitHub user.
     *
     * @Example
     * <ul>
     * <li>- org/repo-name</li>
     * <li>- user/repo-name</li>
     * </ul>
     */
    String repository

    /**
     * Github branch to checkout
     */
    String branch

    /**
     * Folder where the repository is cloned to.
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
     * def github = new GitHub(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    GitHub(steps) {
        this.steps = steps
    }

    /**
     * Initialize github properties
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   repository                  repository name
     * @param   branch                      branch name to work with (like clone, push, etc). Optional,
     *                                      default value is {@link #DEFAULT_BRANCH}.
     * @param   folder                      target folder
     * @param   usernamePasswordCredential  github username/password credential
     * @param   username                    github {@code user.name} config. Optional, can be extracted from credential
     * @param   email                       github {@code user.email} config
     */
    void init(Map args = [:]) {
        if (args['repository']) {
            this.repository = args['repository']
        }
        if (args['branch']) {
            this.branch = args['branch']
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
                // $USERNAME is only available in step.sh/echo, not "\${USERNAME}" directly
                this.username = this.steps.sh(script: "echo \"\${USERNAME}\"", returnStdout: true).trim()
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
     * We can guess repository/branch information from an existing git folder.
     *
     * @Note This method may update {@code #repository} and/or {@code #branch} fields.
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
     * Clone a repository.
     *
     * @Note Ideally this method should be called {@code clone}, which is conflicted with {@code java.lang.Object#clone()}.
     *
     * @Example
     * <pre>
     *     github.cloneRepository(
     *         branch :       'my-branch',
     *         folder :       '.tmp-my-branch'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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
     * Setup git config of username/email based on properties.
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
     * Issue git command and get stdout return.
     *
     * @Example
     * <pre>
     *     String result = github.command('git rev-parse HEAD')
     *     // result should be a string with latest commit hash
     * </pre>
     *
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
     * Checkout a new branch
     *
     * @Note After checkout, the
     *
     * @Example
     * <pre>
     *     github.checkout('feature/test')
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  branch       branch name
     */
    void checkout(Map args = [:]) throws InvalidArgumentException {
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
        if (!args.containsKey('branch') || !args['branch']) {
            throw new InvalidArgumentException('branch')
        }

        this.command("git checkout -b \"${args['branch']}\"")
        this.branch = args['branch']
    }

    /**
     * Checkout a new branch
     *
     * @see #checkout(Map)
     */
    void checkout(String branch) {
        this.checkout(['branch': branch])
    }

    /**
     * Commit all the changes.
     *
     * @Note This method will include all local changes, even though they are not staged.
     *
     * @Note The commit made by this method has sign-off of the git user.
     *
     * @Example
     * <pre>
     *     github.commit('my changes to handle some errors')
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
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

        this.command("git add . && git commit -s -m '${message}'")
    }

    /**
     * Commit all the changes.
     *
     * @see #commit(Map)
     */
    void commit(String message) {
        this.commit(['message': message])
    }

    /**
     * Get last commit information.
     *
     * @Example
     * <pre>
     *     def info = github.getLastCommit(['hash', 'subject'])
     *     echo "Current commit hash    : ${info['hash']}"
     *     echo "Current commit subject : ${info['subject']}"
     * </pre>
     *
     * @param fields    List of what information of the commit should be returned. Available fields are:<ul>
     *                  <li>- hash</li>
     *                  <li>- shortHash</li>
     *                  <li>- author</li>
     *                  <li>- email</li>
     *                  <li>- timestamp</li>
     *                  <li>- date</li>
     *                  <li>- subject</li>
     *                  <li>- body</li>
     *                  </ul>
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
     * Get last commit hash.
     *
     * @Example
     * <pre>
     *     def commitHash = github.getLastCommitHash()
     * </pre>
     *
     * @param returnLong     return long or short hash
     * @return               last commit hash
     */
    String getLastCommitHash(Boolean returnLong = true) {
        Map result = this.getLastCommit(returnLong ? ['hash'] : ['shortHash'])

        return result[returnLong ? 'hash' : 'shortHash']
    }

    /**
     * Push all local commits to remote.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     */
    void push(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        this.command("git push -u origin ${this.branch}")
    }

    /**
     * Check if current working tree is clean or not.
     *
     * @Example
     * <pre>
     *     if (!github.isClean()) {
     *         echo "There are changes not committed."
     *     }
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     *
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
     * Check if current branch is synced with remote.
     *
     * @Example
     * <pre>
     *     if (!github.isSynced()) {
     *         echo "Branch is not synced with remote."
     *     }
     * </pre>
     *
     * @Note This method won't throw exceptions if it's not synced.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     *
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
     * Reset current branch to remote. This is get rid of all local modifications.
     *
     * @Note This method will also do a fetch before reset.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
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
     * Create pull request based on current branch
     *
     * @Note The branch must be synced with remote. Otherwise please push all
     *       changes or reset to remote before creating pull request.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     *
     * @param  title           pull request title to be created
     * @param  base            base branch
     * @param  body            pull request body message
     * @param  draft           boolean, if this is a draft pull request
     * @return                 pull request ID if it's successfully
     */
    Integer createPullRequest(Map args = [:]) throws InvalidArgumentException, ScmException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.repository) {
            throw new InvalidArgumentException('repository')
        }
        def repoSplit = this.repository.split('/')
        if (repoSplit.size() !=  2) {
            throw new InvalidArgumentException('repository', 'Repository should be in username/project format.')
        }
        if (!this.branch) {
            throw new InvalidArgumentException('branch')
        }
        if (!args['title']) {
            throw new InvalidArgumentException('title')
        }
        if (!args['base']) {
            throw new InvalidArgumentException('base')
        }
        if (!this.usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        if (!isSynced(args)) {
            throw new ScmException('Working folder is not synced with remote')
        }

        Map pullRequest = this.steps.readJSON text: '{}'
        pullRequest['title'] = "${args['title']}".toString()
        pullRequest['head'] = "${repoSplit[0]}:${this.branch}".toString()
        pullRequest['base'] = "${args['base']}".toString()

        if (args.containsKey('body') && args['body']) {
            pullRequest['body'] = "${args['body']}".toString()
        }
        if (args.containsKey('draft')) {
            pullRequest['draft'] = !!args.containsKey('draft')
        }

        log.fine("creating pull request on ${this.repository}:\n${pullRequest}")

        File tf = File.createTempFile("jenkins-github-",".tmp")
        // it's ok the tmp file is not deleted, we are running in container
        tf.deleteOnExit()
        writeJSON file: tf.absolutePath, json: pullRequest

        def result

        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId: this.usernamePasswordCredential,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )
        ]) {
            resultText = this.steps.sh(
                script: "curl -u \"\${USERNAME}:\${PASSWORD}\" -sS" +
                        " -X POST" +
                        " --data-binary '@${tf.absolutePath}'" +
                        " \"${GITHUB_API_DOMAIN}/repos/${this.repository}/pulls\"",
                returnStdout: true
            ).trim()

            log.finer("creating pull request on ${this.repository} response:\n${resultText}")

            result = this.steps.readJSON text: resultText
        }
        if (!result || !result.containsKey('number')) {
            throw new ScmException("Invalid Github API response \"${resultText}\"")
        }

        return result['number']
    }

    /**
     * Create pull request based on current branch
     *
     * @see #createPullRequest(Map)
     */
    Integer createPullRequest(String base, String title, String body = '') {
        return this.createPullRequest([
            'base'  : base,
            'title' : title,
            'body'  : body
        ])
    }

    /**
     * Return infomation of a pull request
     *
     * @see https://developer.github.com/v3/pulls/#response-1
     *
     * @param  pr           pull request id
     * @return              pull request object
     */
    Map getPullRequest(Map args = [:]) throws InvalidArgumentException, ScmException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.repository) {
            throw new InvalidArgumentException('repository')
        }
        def repoSplit = this.repository.split('/')
        if (repoSplit.size() !=  2) {
            throw new InvalidArgumentException('repository', 'Repository should be in username/project format.')
        }
        if (!args.containsKey('pr') || !args['pr']) {
            throw new InvalidArgumentException('pr')
        }
        if (!this.usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        Map result

        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId: this.usernamePasswordCredential,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )
        ]) {
            resultText = this.steps.sh(
                script: "curl -u \"\${USERNAME}:\${PASSWORD}\" -sS" +
                        " -X GET" +
                        " \"${GITHUB_API_DOMAIN}/repos/${this.repository}/pulls/${args['pr']}\"",
                returnStdout: true
            ).trim()

            log.finer("fetching pull request #${args['pr']} on ${this.repository} response:\n${resultText}")

            result = this.steps.readJSON text: resultText
        }
        if (!result || !result.containsKey('id')) {
            throw new ScmException("Invalid Github API response \"${resultText}\"")
        }

        return result
    }

    /**
     * Return infomation of a pull request
     *
     * @see #getPullRequest(Map)
     */
    Map getPullRequest(Integer pr) {
        return this.getPullRequest(['pr': pr])
    }

    /**
     * Close pull request
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     *
     * @param  pr              pull request id
     * @return                 true if succeed
     */
    Boolean closePullRequest(Map args = [:]) throws InvalidArgumentException, ScmException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.repository) {
            throw new InvalidArgumentException('repository')
        }
        def repoSplit = this.repository.split('/')
        if (repoSplit.size() !=  2) {
            throw new InvalidArgumentException('repository', 'Repository should be in username/project format.')
        }
        if (!args['pr']) {
            throw new InvalidArgumentException('pr')
        }
        if (!this.usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        Map pr = getPullRequest(args['pr'])
        // Map prUpdate = this.steps.readJSON text: '{}'
        // prUpdate['title'] = "${args['title']}".toString()
        // prUpdate['head'] = "${repoSplit[0]}:${this.branch}".toString()
        // prUpdate['base'] = "${pr['base']}".toString()

        // prUpdate['title'] = "${pr['title']}".toString()
        // prUpdate['body'] = "${pr['body']}".toString()
        // prUpdate['state'] = 'close'

        // log.fine("creating pull request on ${this.repository}:\n${prUpdate}")

        // File tf = File.createTempFile("jenkins-github-",".tmp")
        // // it's ok the tmp file is not deleted, we are running in container
        // tf.deleteOnExit()
        // writeJSON file: tf.absolutePath, json: pullRequest

        def result

        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId: this.usernamePasswordCredential,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )
        ]) {
            resultText = this.steps.sh(
                script: "curl -u \"\${USERNAME}:\${PASSWORD}\" -sS" +
                        " -X PATCH" +
                        " --data '{\"state\":\"close\"}'" +
                        " \"${GITHUB_API_DOMAIN}/repos/${this.repository}/pulls/${args['pr']}\"",
                returnStdout: true
            ).trim()

            log.finer("creating pull request on ${this.repository} response:\n${resultText}")

            result = this.steps.readJSON text: resultText
        }
        if (!result || !result.containsKey('number')) {
            throw new ScmException("Invalid Github API response \"${resultText}\"")
        }

        return result['number']
    }

    /**
     * Tag the branch and push to remote.
     *
     * @Note Currently only support lightweighted tag.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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

    /**
     * Tag the branch and push to remote.
     *
     * @Note Currently only support lightweighted tag.
     *
     * @param  tag           tag name to be created
     *
     * @see #tag(Map)
     */
    void tag(String tag) {
        this.tag(['tag': tag])
    }

    /**
     * Validate if a tag exists in local.
     *
     * @Example
     * <pre>
     *     if (github.tagExistsLocal('v1.2.3')) {
     *         echo "Tag v1.2.3 already exists in local."
     *     }
     * </pre>
     *
     * @param tag     tag name to check
     * @return        true/false
     */
    Boolean tagExistsLocal(String tag) {
        def localTags = this.command("git tag --list").split("\n")
        def foundTag = false

        localTags.each{
            if (it.trim() == tag) { foundTag = true }
        }

        return foundTag
    }

    /**
     * Validate if a tag exists in remote.
     *
     * @Example
     * <pre>
     *     if (github.tagExistsRemote('v1.2.3')) {
     *         echo "Tag v1.2.3 already exists in remote."
     *     }
     * </pre>
     *
     * @param tag     tag name to check
     * @return        true/false
     */
    Boolean tagExistsRemote(String tag) {
        def remotedTags = this.command("git ls-remote --tags").split("\n")
        def foundTag = false

        remotedTags.each{
            if (it.endsWith("refs/tags/${tag}")) { foundTag = true }
        }

        return foundTag
    }
}
