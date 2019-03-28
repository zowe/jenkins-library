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
    public static final String PACKAGE_JSON = 'pacakge.json'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * npm registry url
     */
    String registry = 'https://registry.npmjs.org/'

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
     * GitHub instance
     */
    GitHub github

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
     * @param options  Options to initialize npm registry instance
     */
    Registry(steps, Map options = [:]) {
        this.steps = steps

        // init default property values
        if (options.size() > 0) {
            this.init(options)
        }
    }

    /**
     * Initialize npm registry properties
     * @param   registry         the registry URL
     * @param   tokenCredential  Jenkins credential ID for NPM token
     * @param   email            NPM user email
     */
    void init(Map args = [:]) {
        if (args['registry']) {
            registry = args['registry']
        }
        if (!registry) {
            // try to detect from package.json if not defined
            registry = this.detect()
        }
        if (args['email']) {
            email = args['email']
        }
        if (args['tokenCredential']) {
            tokenCredential = args['tokenCredential']
        }
        if (args['github']) {
            github = args['github']
        }
    }

    /**
    * Detect npm registry from package.json
    *
    * @param  file     file name of package.json.
    * @return          the registry url if found
    */
    String detect(Map args = [:]) {
        String registry

        def file = args['file'] ? args['file'] : PACKAGE_JSON

        if (fileExists(file)) {
            def pkg = this.steps.readJSON(file: file)
            if (pkg && pkg['publishConfig'] && pkg['publishConfig']['registry']) {
                registry = pkg['publishConfig']['registry']
            }
        }

        return registry
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
        def steps = this.steps

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

        steps.echo "login to npm registry: ${registry}"

        // create if it's not existed
        // backup current .npmrc
        steps.sh "touch ${NPMRC_FILE} && mv ${NPMRC_FILE} ${NPMRC_FILE}-bak"

        // update auth in .npmrc
        steps.withCredentials([string(credentialsId: tokenCredential, variable: 'TOKEN')]) {
            steps.sh """
npm config set registry ${registry}
npm config set _auth ${TOKEN}
npm config set email ${email}
npm config set always-auth true
"""
        }

        // get login information
        def whoami = steps.sh(script: "npm whoami", returnStdout: true).trim()
        steps.echo "npm user: ${whoami}"

        return whoami
    }

    String _getTempfolder() {
        String ts = Instant.now().toString().replaceAll(/[^0-9]/, '')

        return ".tmp-npm-registry-${ts}"
    }

    void version(String branch, String version = 'PATCH') throws InvalidArgumentException {
        // validate arguments
        if (!github) {
            throw new InvalidArgumentException('github')
        }
        if (!branch) {
            throw new InvalidArgumentException('branch')
        }
        def tempFolder = _getTempfolder()

        github.clone([
            'branch'      : branch,
            'shallow'     : true,
            'targetFolder': tempFolder
        ])

        github.push()

        withCredentials([usernamePassword(
            credentialsId: crendential,
            passwordVariable: 'GIT_PASSWORD',
            usernameVariable: 'GIT_USERNAME'
        )]) {
        // checkout repository, bump version and push back
        sh """
git clone --depth 1 https://github.com/${repository}.git -b "${branch}" "${tempFolder}"
cd ${tempFolder}
npm version ${version}
git push 'https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${repository}.git'
cd ..
rm -fr ${tempFolder}
"""
        }
    }
}
