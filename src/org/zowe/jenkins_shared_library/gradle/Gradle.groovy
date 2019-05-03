/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.gradle

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.scm.GitHub
import org.zowe.jenkins_shared_library.Utils

@Log
class Gradle {
    /**
     * Default file name of gradle.properties
     */
    public static final String GRADLE_PROPERTIES = 'gradle.properties'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Package information extracted from package.json
     */
    Map _packageInfo

    /**
     * package.json file name, default is PACKAGE_JSON
     */
    String versionDefinitionFile = GRADLE_PROPERTIES

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def npm = new Gradle(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Gradle(steps) {
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize gradle project properties
     * @param   versionDefinitionFile             file where defines `version` of the project
     */
    void init(Map args = [:]) {
        if (args['versionDefinitionFile']) {
            this.versionDefinitionFile = args['versionDefinitionFile']
        }
        if (!this.versionDefinitionFile) {
            this.versionDefinitionFile = GRADLE_PROPERTIES
        }

        // bootstrap
        this.bootstrap()
    }

    /**
     * Bootstrap gradle
     */
    void bootstrap() {
        if (steps.fileExists('bootstrap_gradlew.sh')) {
            // we need to bootstrap gradle
            steps.sh './bootstrap_gradlew.sh'
        }
    }

    /**
     * Get current package information from package.json
     *
     * NOTE: this method has cache. If you need to reload package info from package.json, run method
     * #clearPackageInfoCache()
     *
     * @return             current package information including name, version, description, license, etc
     */
    Map getPackageInfo() {
        if (this._packageInfo) {
            return this._packageInfo
        }

        Map info = [:]

        if (!_isVersionDefinedInGradleProperties()) {
            throw new GradleException("version must be defined in ${this.versionDefinitionFile}")
        }

        // load properties
        def properties = this.steps.sh(script: './gradlew properties -q', returnStdout: true).trim()
        properties.split("\n").each { line ->
            def matches = line =~ /^([a-zA-Z0-9]+):\s+(.+)$/
            if (matches.matches() && matches[0] && matches[0].size() == 3) {
                def key = matches[0][1]
                def val = matches[0][2]

                if (key == 'name') {
                    info[key] = val
                } else if (key == 'version') {
                    info[key] = val
                    info['versionTrunks'] = Utils.parseSemanticVersion(val)
                } else if (key == 'group') {
                    this.packageName = val
                } else if (key == 'description' && val != 'null') {
                    info[key] = val
                }
            }
        }

        // load tasks
        info['scripts'] = []
        def tasks = this.steps.sh(script: './gradlew tasks -q', returnStdout: true).trim()
        tasks.split("\n").each { line ->
            def matches = line =~ /^([a-zA-Z0-9]+) - (.+)$/
            if (matches.matches() && matches[0] && matches[0].size() == 3) {
                info['scripts'].push(matches[0][1])
            }
        }

        log.fine("Package information of: ${info}")
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
     * This checks if version entry is defined in gradle.properties.
     *
     * This is pre-req to perform a gradle release (bump version)
     *
     * @return            true/false
     */
    Boolean _isVersionDefinedInGradleProperties() {
        if (steps.fileExists(this.versionDefinitionFile)) {
            steps.echo "Found ${this.versionDefinitionFile}."
            def versionLine = this.steps.sh(script: "cat ${this.versionDefinitionFile} | grep '^version\\s*=\\s*.\\+\$'", returnStdout: true).trim()
            if (versionLine) {
                steps.echo "${versionLine} is defined in ${this.versionDefinitionFile}."
                return true
            } else {
                steps.echo "Version is not defined in ${this.versionDefinitionFile}."
            }
        } else {
            steps.echo "${this.versionDefinitionFile} does not exist."
        }

        return false
    }

    /**
     * Declare a new version of gradle project
     *
     * @param github         GitHub instance must have been initialized with repository, credential, etc
     * @param branch         which branch to release
     * @param version        what kind of version bump we should make
     */
    void version(Map args = [:]) throws InvalidArgumentException, GradleException {
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
        String version = args.containsKey('version') ? args['version'] : 'PATCH'
        version = version.toLowerCase()
        String newSemVer = ''

        // get temp folder for cloning
        def tempFolder = ".tmp-gradle-${Utils.getTimestamp()}"
        def oldBranch = args['github'].getBranch()
        def oldFolder = args['github'].getFolder()

        this.steps.echo "Cloning ${args['branch']} into ${tempFolder} ..."
        // clone to temp folder
        args['github'].cloneRepository([
            'branch'   : args['branch'],
            'folder'   : tempFolder
        ])
        if (!args['github'].isClean()) {
            throw new GradleException('Git working directory not clean.')
        }

        // run npm version
        this.steps.echo "Making a \"${version}\" version bump ..."
        this.steps.dir(tempFolder) {
            if (!this.packageInfo || !this.packageInfo['versionTrunks']) {
                throw new GradleException('Version is not successfully extracted from project.')
            }

            if (version == 'patch') {
                newSemVer = "${this.packageInfo['versionTrunks']['major']}" +
                             ".${this.packageInfo['versionTrunks']['minor']}" +
                             ".${this.packageInfo['versionTrunks']['patch'] + 1}"
            } else if (version == 'minor') {
                newSemVer = "${this.packageInfo['versionTrunks']['major']}" +
                             ".${this.packageInfo['versionTrunks']['minor'] + 1}" +
                             ".${this.packageInfo['versionTrunks']['patch']}"
            } else if (version == 'major') {
                newSemVer = "${this.packageInfo['versionTrunks']['major'] + 1}" +
                             ".${this.packageInfo['versionTrunks']['minor']}" +
                             ".${this.packageInfo['versionTrunks']['patch']}"
            } else if (version =~ /[0-9]+\.[0-9]+\.[0-9]+/) {
                newSemVer = version
            } else {
                throw new GradleException("New version \"${version}\" is not accepted.")
            }

            steps.sh "sed -e \"s#^version=.*\\\$#version=${newSemVer}#\" ${this.versionDefinitionFile} > .${this.versionDefinitionFile}.tmp"
            // compare if we successfully bumped the version
            String beforeConvert = steps.readFile "${this.versionDefinitionFile}"
            String afterConvert = steps.readFile ".${this.versionDefinitionFile}.tmp"
            log.finer("Before convert:\n${beforeConvert}")
            log.finer("After convert:\n${afterConvert}")
            if (beforeConvert == afterConvert) {
                throw new GradleException('Version bump is not successfully.')
            }

            // replace version
            steps.sh 'mv .${this.versionDefinitionFile}.tmp ${this.versionDefinitionFile}'
        }

        // commit
        args['github'].commit(newSemVer)

        // push version changes
        this.steps.echo "Pushing ${args['branch']} to remote ..."
        args['github'].push()
        if (!args['github'].isSynced()) {
            throw new GradleException('Branch is not synced with remote after npm version.')
        }

        // remove temp folder
        this.steps.echo "Removing temporary folder ${tempFolder} ..."
        this.steps.sh "rm -fr ${tempFolder}"

        // set values back
        args['github'].setBranch(oldBranch)
        args['github'].setFolder(oldFolder)
    }

    /**
     * Declare a new version of gradle project
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
