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
        if (args['folder']) {
            this.folder = args['folder']
        }
    }

    /**
     * Clone a repository
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param  shallow       if do a shallow clone (with depth 1)
     * @param  branch        branch to checkout
     * @param  folder        which folder to save the cloned files
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
        def folderOpt = args['folder'] ? " '${args['folder']}'" : ''

        this.steps.sh "git clone ${depthOpt} 'https://${GITHUB_DOMAIN}/${repository}.git'${branchOpt}${folderOpt}"
    }

    /**
     * Issue git command and get stdout return
     * @param  command     git command
     * @return             stdout log
     */
    String command(String command) {
        return this.steps.sh(script: "cd '${this.folder}' && ${command}", returnStdout: true).trim()
    }

    /**
     * Commit changes
     */
    void commit(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        def message = args['message'] ?
            args['message'] :
            "Automated commit from ${env.JOB_NAME}#${env.BUILD_NUMBER} by \"${this.username}\" \"${this.email}\""

        this.command("git add . && git commit -m '${message}'")
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
        Map result = []

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
            if (fields[entry.key]) {
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
            this.command("git push 'https://${USERNAME}:${PASSWORD}@${GITHUB_DOMAIN}/${repository}.git'")
        }
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

        String status = this.command('git status')
        return status.matches("Your branch is up to date with '[^']+'.")
    }


    /**
     * Tag the branch
     */
    void tag(Map args = [:]) {
        throw new UnderConstructionException('GitHub.tag() method is not implemented yet.')
    }
}
