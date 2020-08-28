/*
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

/**
 * Methods to handle NPM project.
 *
 * @Example
 * <pre>
 *     def npm = new Registry(this)
 *     // init npm registry
 *     npm.init(
 *         email                      : 'artifactory-user@gmail.com',
 *         usernamePasswordCredential : 'my-artifactory-credential',
 *         registry                   : 'https://my-project.jfrog.io/my-project/api/npm/npm-release/',
 *         scope                      : 'zowe'
 *     )
 * </pre>
 */
@Log
class Registry {
    /**
     * Constant of {@code .npmrc} file name
     *
     * @Default {@code "~/.npmrc"}
     */
    public static final String NPMRC_FILE = '~/.npmrc'

    /**
     * Constant of {@code package.json} file name
     *
     * @Default {@code "package.json"}
     */
    public static final String PACKAGE_JSON = 'package.json'

    /**
     * Default npmjs registry.
     *
     * @Default {@code "https://registry.npmjs.org/"}
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
     * Jenkins credential ID for NPM token.
     *
     * @Note The content of token could be base64 encoded "username:password".
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
     * File name of {@code package.json}.
     *
     * @Default {@link #PACKAGE_JSON}
     */
    String packageJsonFile = PACKAGE_JSON

    /**
     * Package information extracted from {@link #PACKAGE_JSON}.
     */
    Map _packageInfo

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
     * Initialize npm registry properties.
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   registry                    the registry URL
     * @param   tokenCredential             Jenkins credential ID for NPM token. Optional.
     * @param   usernamePasswordCredential  Jenkins credential ID for NPM username/base64_password. Optional.
     * @param   email                       NPM user email
     * @param   packageJsonFile             {@code package.json} file name. Optional, default is {@link #PACKAGE_JSON}.
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
     * Detect npm registry and scope from {@code package.json}.
     *
     * @Note This method is taken out from {@link #init(Map)} because some registries (like install
     * registries) shouldn't have this. Only publish registry makes sense to get from
     * {@code package.json}.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
     */
    void initFromPackageJson(Map args = [:]) {
        Map info = this.getPackageInfo()

        if (!this.registry && info.registry) {
            this.registry = info.registry
        }
        if (!this.scope && info.scope) {
            this.scope = info.scope
        }
    }

    /**
     * Get current package information from {@code package.json}.
     *
     * @Note This method has cache. If you need to reload package info from package.json, run method
     * {@link #clearPackageInfoCache()} to reset the cache.
     *
     * <p><strong>Expected keys in the result Map:</strong><ul>
     * <li>{@code name} - name of the package. For example, {@code "explorer-jes"}.</li>
     * <li>{@code scope} - scope of the package. Optional. For example, {@code "zowe"}. Please note this value does <strong>NOT
     *     </strong> have <strong>{@literal @}</strong> included.</li>
     * <li>{@code description} - description of the package if defined. For example, {@code "A UI plugin to handle z/OS jobs."}.</li>
     * <li>{@code version} - version of the package. For example, {@code "1.2.3"}.</li>
     * <li>{@code versionTrunks} - Map version trunks returned from {@link jenkins_shared_library.Utils#parseSemanticVersion(java.lang.String)}.</li>
     * <li>{@code license} - license of the package if defined. For example, {@code "EPL-2.0"}.</li>
     * <li>{@code registry} - publish registry of the package if defined.</li>
     * <li>{@code scripts} - List of scripts of the package defined. For example, {@code ["build", "test", "start", "coverage"]}.</li>
     * </ul></p>
     *
     * @return             current package information including name, version, description, license, etc
     */
    Map getPackageInfo() {
        if (this._packageInfo) {
            return this._packageInfo
        }

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
        this._packageInfo = info

        return info
    }

    /**
     * Reset package info cache
     */
    void clearPackageInfoCache() {
        this._packageInfo = null
    }


    /**
     * Login to NPM registry.
     *
     * @Note Using token credential may receive this error with whoami, but actually npm install is ok.
     * <pre>
     * + npm whoami --registry https://zowe.jfrog.io/zowe/api/npm/npm-release/
     * npm ERR! code E401
     * npm ERR! Unable to authenticate, need: Basic realm="Artifactory Realm"
     * </pre>
     *
     * <p>This happens if we set {@code "//zowe.jfrog.io/zowe/api/npm/npm-release/:_authToken"},
     * but if we set {@code "_auth=<token>"}, everything is ok.</p>
     *
     * <p>Is this a bug of Artifactory?</p>
     *
     * <p>So for now, only usernamePasswordCredential works well.</p>
     *
     * @see <a href="https://www.jfrog.com/confluence/display/RTF/Npm+Registry#NpmRegistry-ConfiguringthenpmClientforaScopeRegistry">jFrog Artifactory - Configuring the npm Client for a Scope Registry</a>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method.
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
                } else {
                    configEntries.push("npm config set registry ${this.registry}")
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
                } else {
                    configEntries.push("npm config set registry ${this.registry}")
                }
                this.steps.sh configEntries.join("\n")
            }
        }

        // debug info: npm configs
        String npmConfig = this.steps.sh(script: "npm config list", returnStdout: true).trim()
        log.finer("NPM config list:\n${npmConfig}")

        // get login information
        def whoami = this.steps.sh(script: "npm whoami --registry ${this.registry}", returnStdout: true).trim()
        this.steps.echo "npm user: ${whoami}"

        return whoami
    }

    /**
     * Reset NPN configurations.
     *
     * @Note This will also reset login information, which effectly logout from all repositories.
     */
    void resetConfig() {
        // remove .npmrc in current folder
        if (this.steps.fileExists('.npmrc')) {
            this.steps.sh "rm -f .npmrc || exit 0"
        }
        // remove .npmrc in home folder
        if (this.steps.fileExists(NPMRC_FILE)) {
            this.steps.sh "rm -f ${NPMRC_FILE} || exit 0"
        }
    }

    /**
     * Run npm audit with default config.
     *
     * @Note {@code npm audit} cannot be ran on private registry, so we reset config before audit.
     */
    void audit() {
        if (this.steps.fileExists(NPMRC_FILE)) {
            this.steps.sh "mv ${NPMRC_FILE} ${NPMRC_FILE}.bak || exit 0"
        }
        try {
            this.steps.sh "npm audit"
        } catch (e) {
            throw e
        } finally {
            if (this.steps.fileExists("${NPMRC_FILE}.bak")) {
                this.steps.sh "mv ${NPMRC_FILE}.bak ${NPMRC_FILE} || exit 0"
            }
        }
    }

    /**
     * Publish npm package with tag.
     *
     * @Example
     * <pre>
     *     // publish package 1.2.3-snapshot-20190101010101 with dev tag
     *     npm.publish(
     *         tag     : 'dev',
     *         version : '1.2.3-snapshot-20190101010101'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param tag          npm publish tag. Optional, default is empty which is (@code latest).
     * @param version      package version to publish.
     */
    void publish(Map args = [:]) {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        String optNpmTag = (args.containsKey('tag') && args['tag']) ? " --tag ${args['tag']}" : ""
        String optNpmRegistry = this.registry ? " --registry ${this.registry}" : ""

        // if we need to reset back
        String currentCommit

        if (args.containsKey('version') && args.version) {
            currentCommit = steps.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()

            try {
                // npm version without tag & commit
                // so this command just update package.json version to target version.
                steps.sh "npm version --no-git-tag-version ${args.version}"
            } catch (err) {
                // ignore error
            }
        }

        steps.sh "npm publish${optNpmTag}${optNpmRegistry}"

        if (currentCommit) {
            try {
                steps.echo "Revert changes by npm version ..."
                steps.sh "git reset --hard ${currentCommit}"
            } catch (err) {
                // ignore error
            }
        }
    }

    /**
     * Declare a new version of npm package.
     *
     * @Note This task will bump the package version defined in package.json, commit the change, and push
     * to GitHub. The commit is signed-off.
     *
     * @Example
     * <pre>
     *     def github = new org.zowe.jenkins_shared_library.scm.GitHub(this)
     *     // bump patch version on master branch
     *     npm.version(
     *         github  :     github,
     *         branch  :     'master',
     *         version :     'patch'
     *     )
     *     // After this, you should be able to see your repository master branch has a commit of
     *     // version bump.
     * </pre>
     *
     * @see jenkins_shared_library.scm.GitHub
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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
            // amend the commit to add signoff
            // NOTE: ideal command is: git rebase HEAD~1 --signoff
            // But --signoff option is not supported by the default git shipped with Debian 9 (Stretch)
            def commitMessage = this.steps.sh(script: "git log -1 --pretty=%s", returnStdout: true).trim()
            this.steps.sh "git reset HEAD~1 && git add . && git commit -s -m \"${commitMessage}\""
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
     * Declare a new version of npm package.
     *
     * @see #version(Map)
     */
    void version(GitHub github, String branch, String version = 'PATCH') {
        this.version([
            'github'  : github,
            'branch'  : branch,
            'version' : version,
        ])
    }
}
