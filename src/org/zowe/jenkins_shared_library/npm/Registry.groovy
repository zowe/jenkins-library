/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.npm

import java.time.Instant
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.scm.GitHub

class Registry {
    /**
     * Constant of .npmrc file name
     */
    public static final String NPMRC_FILE = '~/.npmrc'

    /**
     * Constant of package.json file name
     */
    public static final String PACKAGE_JSON = 'package.json'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * npm registry url
     */
    String registry = 'https://registry.npmjs.org/'

    /**
     * npm package scope
     */
    String scope

    /**
     * Jenkins credential ID for NPM token
     *
     * The content of token could be base64 encoded "username:password"
     */
    String tokenCredential

    /**
     * npm user email, required for publishing
     */
    String email

    /**
     * package.json file name, default is PACKAGE_JSON
     */
    String packageJsonFile

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
    Registry(steps) {
        this.steps = steps
    }

    /**
     * Initialize npm registry properties
     * @param   registry         the registry URL
     * @param   tokenCredential  Jenkins credential ID for NPM token
     * @param   email            NPM user email
     * @param   packageJsonFile  package.json file name
     */
    void init(Map args = [:]) {
        if (args['packageJsonFile']) {
            this.packageJsonFile = args['packageJsonFile']
        }
        if (!this.packageJsonFile) {
            this.packageJsonFile = PACKAGE_JSON
        }
        if (args['registry']) {
            this.registry = args['registry']
        } else {
            // try to detect from package.json if not defined
            String registryInPackageJson = this.getRegistryFromPackageJson()
            if (registryInPackageJson) {
                this.registry = registryInPackageJson
            }
        }
        if (args['scope']) {
            this.scope = args['scope']
        }
        if (args['email']) {
            this.email = args['email']
        }
        if (args['tokenCredential']) {
            this.tokenCredential = args['tokenCredential']
        }
    }

    /**
     * Detect npm registry from package.json
     *
     * @param  packageJsonFile    file name of package.json.
     * @return                    the registry url if found
     */
    String getRegistryFromPackageJson(Map args = [:]) {
        String registry

        if (this.packageJsonFile && this.steps.fileExists(this.packageJsonFile)) {
            def pkg = this.steps.readJSON(file: this.packageJsonFile)
            if (pkg && pkg['publishConfig'] && pkg['publishConfig']['registry']) {
                registry = pkg['publishConfig']['registry']
            }
        }

        return registry
    }

    /**
     * Get current package information from package.json
     * @return             current package information including name, version, description, license, etc
     */
    Map getPackageInfo() {
        Map info = [:]

        if (this.packageJsonFile && this.steps.fileExists(this.packageJsonFile)) {
            def pkg = this.steps.readJSON(file: this.packageJsonFile)
            if (pkg) {
                if (pkg['name']) {
                    info['name'] = pkg['name']
                    def matches = info['name'] =~ /^@([^\/]+)\/(.+)$/
                    if (matches && matches[0]) {
                        info['scope'] = matches[0][1]
                        this.scope = info['scope']
                        info['name'] = matches[0][2]
                    }
                }
                if (pkg['description']) {
                    info['description'] = pkg['description']
                }
                if (pkg['version']) {
                    info['version'] = pkg['version']
                    def matches = info['version'] =~ /^([0-9]+)\.([0-9]+)\.([0-9]+)(.*)$/
                    if (matches && matches[0]) {
                        info['versionTrunks'] = [:]
                        info['versionTrunks']['major'] = matches[0][1].toInteger()
                        info['versionTrunks']['minor'] = matches[0][2].toInteger()
                        info['versionTrunks']['patch'] = matches[0][3].toInteger()
                        info['versionTrunks']['metadata'] = matches[0][4]
                    } else {
                        this.steps.echo "WARNING: version \"${info['version']}\" is not a semantic version."
                    }
                }
                if (pkg['license']) {
                    info['license'] = pkg['license']
                }
            }
        }

        return info
    }

    /**
     * Login to NPM registry
     *
     * @param   registry         the registry URL
     * @param   tokenCredential  Jenkins credential ID for NPM token
     * @param   email            NPM user email
     * @return                   username who login
     */
    String login(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!registry) {
            throw new InvalidArgumentException('registry')
        }
        if (!email) {
            throw new InvalidArgumentException('email')
        }
        if (!tokenCredential) {
            throw new InvalidArgumentException('token')
        }

        this.steps.echo "login to npm registry: ${registry}"

        // create if it's not existed
        // backup current .npmrc
        this.steps.sh "touch ${NPMRC_FILE} && mv ${NPMRC_FILE} ${NPMRC_FILE}-bak"

        // update auth in .npmrc
        this.steps.withCredentials([
            this.steps.string(
                credentialsId: tokenCredential,
                variable: 'TOKEN'
            )
        ]) {
            this.steps.sh """
npm config set registry ${this.registry}
npm config set _auth \${TOKEN}
npm config set email ${this.email}
npm config set always-auth true
"""
        }

        // get login information
        def whoami = this.steps.sh(script: "npm whoami", returnStdout: true).trim()
        this.steps.echo "npm user: ${whoami}"

        return whoami
    }

    String _getTempfolder() {
        String ts = Instant.now().toString().replaceAll(/[^0-9]/, '')

        return ".tmp-npm-registry-${ts}"
    }

    /**
     * Declare a new version of npm package
     *
     * @param github         GitHub instance must have been initialized with repository, credential, etc
     * @param branch         which branch to release
     * @param version        what kind of version bump we should make
     */
    void version(GitHub github, String branch, String version = 'PATCH') throws InvalidArgumentException {
        // validate arguments
        if (!github) {
            throw new InvalidArgumentException('github')
        }
        if (!branch) {
            throw new InvalidArgumentException('branch')
        }

        // get temp folder for cloning
        def tempFolder = _getTempfolder()
        def oldBranch = github.getBranch()
        def oldFolder = github.getFolder()

        this.steps.echo "Cloning ${branch} into ${tempFolder} ..."
        // clone to temp folder
        github.cloneRepository([
            'branch'   : branch,
            'folder'   : tempFolder
        ])

        // run npm version
        this.steps.echo "Making a \"${version}\" version bump ..."
        this.steps.dir(tempFolder) {
            def res = this.steps.sh(script: "npm version ${version.toLowerCase()}", returnStdout: true).trim()
            if (res.contains('Git working directory not clean.')) {
                throw new Exception('Working directory is not clean')
            } else if (!(res ==~ /^v[0-9]+\.[0-9]+\.[0-9]+$/)) {
                throw new Exception("Bump version failed: ${res}")
            }
        }

        // push version changes
        this.steps.echo "Pushing ${branch} to remote ..."
        github.push()
        if (!github.isSynced()) {
            throw new Exception('Branch is not synced with remote after npm version.')
        }

        // remove temp folder
        this.steps.echo "Removing temporary folder ${tempFolder} ..."
        this.steps.sh "rm -fr ${tempFolder}"

        // set values back
        github.setBranch(oldBranch)
        github.setFolder(oldFolder)
    }
}
