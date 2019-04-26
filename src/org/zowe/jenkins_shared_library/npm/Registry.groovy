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

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.scm.GitHub
import org.zowe.jenkins_shared_library.Utils

@Log
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
     * Default npmjs registry
     */
    public static final String DEFAULT_REGISTRY = 'https://registry.npmjs.org/'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * npm registry url
     */
    String registry

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
     * Jenkins credential ID for NPM username/base64_password
     */
    String usernamePasswordCredential

    /**
     * npm user email, required for publishing
     */
    String email

    /**
     * package.json file name, default is PACKAGE_JSON
     */
    String packageJsonFile = PACKAGE_JSON

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
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize npm registry properties
     * @param   registry                    the registry URL
     * @param   tokenCredential             Jenkins credential ID for NPM token
     * @param   usernamePasswordCredential  Jenkins credential ID for NPM username/base64_password
     * @param   email                       NPM user email
     * @param   packageJsonFile             package.json file name
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
        }
        // normalize registry url
        if (this.registry) {
            if (!registry.endsWith('/')) {
                registry += '/'
            }
            registry = registry.toLowerCase()
        }
        if (args['scope']) {
            this.scope = args['scope']
            if (this.scope.startsWith('@')) {
                this.scope = this.scope.substring(1)
            }
        }
        if (args['email']) {
            this.email = args['email']
        }
        if (args['tokenCredential']) {
            this.tokenCredential = args['tokenCredential']
        }
        if (args['usernamePasswordCredential']) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']
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
                    info['versionTrunks'] = Utils.parseSemanticVersion(info['version'])
                }
                if (pkg['license']) {
                    info['license'] = pkg['license']
                }
                if (pkg['publishConfig'] && pkg['publishConfig']['registry']) {
                    info['registry'] = pkg['publishConfig']['registry']
                }
                if (pkg['scripts']) {
                    info['scripts'] = []
                    pkg['scripts'].each { k, v ->
                        info['scripts'].push(k)
                    }
                }
            }
        } else {
            throw new NpmException("packageJsonFile is not defined or file \"${this.packageJsonFile}\" doesn't not exist.")
        }

        log.fine("Package information of ${this.packageJsonFile}: ${info}")

        return info
    }

    /**
     * Login to NPM registry
     *
     * NOTE:
     *
     * Using token credential may receive this error with whoami, but actually npm install is ok.
     * + npm whoami --registry https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-release/
     * npm ERR! code E401
     * npm ERR! Unable to authenticate, need: Basic realm="Artifactory Realm"
     *
     * This happens if we set "//gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-release/:_authToken",
     * but if we set "_auth=<token>", everything is ok.
     *
     * Is this a bug of Artifactory?
     *
     * So for now, only usernamePasswordCredential works well.
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @return                              username who login
     */
    String login(Map args = [:]) throws InvalidArgumentException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!registry) {
            registry = DEFAULT_REGISTRY
        }
        if (!email) {
            throw new InvalidArgumentException('email')
        }
        if (!tokenCredential && !usernamePasswordCredential) {
            throw new InvalidArgumentException('token')
        }

        // per registry authentication in npmrc is:
        // //registry.npmjs.org/:_authToken=<token>
        // without protocol, with _authToken key
        def registryWithoutProtocol
        if (registry.startsWith('https://')) {
            registryWithoutProtocol = registry.substring(6)
        } else if (registry.startsWith('http://')) {
            registryWithoutProtocol = registry.substring(5)
        } else {
            throw new InvalidArgumentException('registry', "Unknown registry protocol")
        }

        this.steps.echo "login to npm registry: ${registry}"

        // create if it's not existed
        // backup current .npmrc
        // this.steps.sh "touch ${NPMRC_FILE} && mv ${NPMRC_FILE} ${NPMRC_FILE}-bak"

        // Prevent npm publish from being affected by the local npmrc file
        if (this.steps.fileExists('.npmrc')) {
            this.steps.sh "rm -f .npmrc || exit 0"
        }

        // update auth in .npmrc
        if (tokenCredential) {
            this.steps.withCredentials([
                this.steps.string(
                    credentialsId: tokenCredential,
                    variable: 'TOKEN'
                )
            ]) {
                List<String> configEntries = ['set +x']
                configEntries.push("npm config set _auth \${TOKEN}")
                configEntries.push("npm config set email ${this.email}")
                configEntries.push("npm config set always-auth true")
                if (this.scope) {
                    configEntries.push("npm config set @${this.scope}:registry ${this.registry}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:_authToken \${TOKEN}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:email ${this.email}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:always-auth true")
                }
                this.steps.sh configEntries.join("\n")
            }
        } else if (usernamePasswordCredential) {
            this.steps.withCredentials([
                this.steps.usernamePassword(
                    credentialsId: this.usernamePasswordCredential,
                    passwordVariable: 'PASSWORD',
                    usernameVariable: 'USERNAME'
                )
            ]) {
                String u = this.steps.sh(script: "echo \"\${USERNAME}\"", returnStdout: true).trim()
                String p = this.steps.sh(script: "echo \"\${PASSWORD}\"", returnStdout: true).trim()
                String base64Password = p.bytes.encodeBase64().toString()
                String base64UsernamePassword = "${u}:${p}".bytes.encodeBase64().toString()
                List<String> configEntries = ['set +x']
                configEntries.push("npm config set _auth ${base64UsernamePassword}")
                configEntries.push("npm config set email ${this.email}")
                configEntries.push("npm config set always-auth true")
                if (this.scope) {
                    configEntries.push("npm config set @${this.scope}:registry ${this.registry}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:username ${u}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:_password ${base64Password}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:email ${this.email}")
                    configEntries.push("npm config set ${registryWithoutProtocol}:always-auth true")
                }
                this.steps.sh configEntries.join("\n")
            }
        }

        // show npm configs
        this.steps.sh 'npm config list'

        // get login information
        def whoami = this.steps.sh(script: "npm whoami --registry ${this.registry}", returnStdout: true).trim()
        this.steps.echo "npm user: ${whoami}"

        return whoami
    }

    /**
     * Publish npm package with tag
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param tag          npm publish tag, default is empty which is (latest)
     * @param version      package version to publish
     */
    void publish(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        String optNpmTag = (args.containsKey('tag') && args['tag']) ? " --tag ${args['tag']}" : ""
        String optNpmRegistry = this.registry ? " --registry ${this.registry}" : ""

        if (args.containsKey('version') && args.version) {
            try {
                steps.sh "npm version ${args.version}"
            } catch (err) {
                steps.echo "${err}"
                steps.sh "git tag v${args.version}"
            }
        }

        steps.sh "npm publish${optNpmTag}${optNpmRegistry}"
    }

    /**
     * Declare a new version of npm package
     *
     * @param github         GitHub instance must have been initialized with repository, credential, etc
     * @param branch         which branch to release
     * @param version        what kind of version bump we should make
     */
    void version(Map args = [:]) throws InvalidArgumentException, NpmException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        // validate arguments
        if (!args['github']) {
            throw new InvalidArgumentException('github')
        }
        if (!args['branch']) {
            throw new InvalidArgumentException('branch')
        }
        def version = args.containsKey('version') ? args['version'] : 'PATCH'

        // get temp folder for cloning
        def tempFolder = ".tmp-npm-registry-${Utils.getTimestamp()}"
        def oldBranch = args['github'].getBranch()
        def oldFolder = args['github'].getFolder()

        this.steps.echo "Cloning ${args['branch']} into ${tempFolder} ..."
        // clone to temp folder
        args['github'].cloneRepository([
            'branch'   : args['branch'],
            'folder'   : tempFolder
        ])

        // run npm version
        this.steps.echo "Making a \"${version}\" version bump ..."
        this.steps.dir(tempFolder) {
            def res = this.steps.sh(script: "npm version ${version.toLowerCase()}", returnStdout: true).trim()
            if (res.contains('Git working directory not clean.')) {
                throw new NpmException('Working directory is not clean')
            } else if (!(res ==~ /^v[0-9]+\.[0-9]+\.[0-9]+$/)) {
                throw new NpmException("Bump version failed: ${res}")
            }
        }

        // push version changes
        this.steps.echo "Pushing ${args['branch']} to remote ..."
        args['github'].push()
        if (!args['github'].isSynced()) {
            throw new NpmException('Branch is not synced with remote after npm version.')
        }

        // remove temp folder
        this.steps.echo "Removing temporary folder ${tempFolder} ..."
        this.steps.sh "rm -fr ${tempFolder}"

        // set values back
        args['github'].setBranch(oldBranch)
        args['github'].setFolder(oldFolder)
    }

    /**
     * Declare a new version of npm package
     *
     * @param github         GitHub instance must have been initialized with repository, credential, etc
     * @param branch         which branch to release
     * @param version        what kind of version bump we should make
     */
    void version(GitHub github, String branch, String version = 'PATCH') {
        this.version([
            'github'  : github,
            'branch'  : branch,
            'version' : version,
        ])
    }
}
