/*
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

/**
 * Methods to handle gradle project.
 *
 * @Example
 * <pre>
 *     def gradle = new Gradle(this)
 *     def info = gradle.getPackageInfo()
 *     // show current package version
 *     echo info['version']
 *     // we need a GitHub instance to bump version
 *     def github = new org.zowe.jenkins_shared_library.scm.GitHub(this)
 *     // bump patch version on master branch
 *     gradle.version(
 *         github  :     github,
 *         branch  :     'master',
 *         version :     'patch'
 *     )
 * </pre>
 */
@Log
class Gradle {
    /**
     * Default file name of {@code gradle.properties}.
     */
    public static final String GRADLE_PROPERTIES = 'gradle.properties'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Package information extracted from gradle settings.
     */
    Map _packageInfo

    /**
     * File name which defined package version.
     *
     * @Default {@link #GRADLE_PROPERTIES}.
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
     * def gradle = new Gradle(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Gradle(steps) {
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize gradle project properties.
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   versionDefinitionFile             file where defines {@code version} of the project.
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
     * Bootstrap gradle.
     *
     * <p>Usually the bootstrap is for downloading {@code gradle/wrapper/gradle-wrapper.jar} to
     * local, so we don't need to upload the {@code gradle-wrapper.jar} into GitHub repository.</p>
     */
    void bootstrap() {
        if (steps.fileExists('bootstrap_gradlew.sh')) {
            // we need to bootstrap gradle
            steps.sh './bootstrap_gradlew.sh'
        }
    }

    /**
     * Get current package information from gradle settings.
     *
     * @Note This method has cache. If you need to reload package info from gradle settings, run method
     * {@link #clearPackageInfoCache()} to reset the cache.
     *
     * <p><strong>Expected keys in the result Map:</strong><ul>
     * <li>{@code name} - name of the package. For example, {@code "explorer-jobs"}.</li>
     * <li>{@code version} - version of the package. For example, {@code "1.2.3"}.</li>
     * <li>{@code versionTrunks} - Map version trunks returned from {@link jenkins_shared_library.Utils#parseSemanticVersion(java.lang.String)}.</li>
     * <li>{@code group} - group name of the package if defined. For example, {@code "org.zowe.explorer.jobs"}.</li>
     * <li>{@code description} - description of the package if defined. For example, {@code "An API to handle z/OS jobs."}.</li>
     * <li>{@code scripts} - List of tasks of the package defined. For example, {@code ["build", "buildDir", "class", "assemble", "release", ...]}.</li>
     * </ul></p>
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
                    info[key] = val
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
     * This checks if version entry is defined in {@link #versionDefinitionFile}.
     *
     * @Note This is pre-req to perform a gradle release (bump version).
     *
     * @Note this method doesn't throw exception if the version is not defined in {@link #versionDefinitionFile}.
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
     * Update version and releaseType in {@link #versionDefinitionFile}.
     *
     * <p>There are 2 properties should be defined in {@link #versionDefinitionFile}:<ul>
     * <li>- version: for example: 1.2.3</li>
     * <li>- releaseMode: either release or snapshot</li>
     * </ul></p>
     *
     * @param version          new semantic version string. For example, {@code "1.2.3"}.
     * @param isRelease        if current build is for release purpose.
     */
    void updateVersionForBuild(String version, Boolean isRelease) {
        def releaseMode = isRelease ? 'release' : 'snapshot'
        steps.sh "sed -e \"s#^version=.*\\\$#version=${version.toUpperCase()}#\" -e \"s#^releaseMode=.*\\\$#releaseMode=${releaseMode}#\" ${this.versionDefinitionFile} > .${this.versionDefinitionFile}.tmp"
        // verify if releaseMode is in place
        def releaseModeLine = this.steps.sh(script: "cat .${this.versionDefinitionFile}.tmp | grep releaseMode=", returnStdout: true).trim()
        if (!releaseModeLine) {
            // make sure we have releaseMode line
            steps.sh "echo >> .${this.versionDefinitionFile}.tmp"
            steps.sh "echo 'releaseMode=${releaseMode}' >> .${this.versionDefinitionFile}.tmp"
        }

        // log for debugging
        String beforeConvert = steps.readFile "${this.versionDefinitionFile}"
        String afterConvert = steps.readFile ".${this.versionDefinitionFile}.tmp"
        log.finer("Before convert:\n${beforeConvert}")
        log.finer("After convert:\n${afterConvert}")

        // replace version
        steps.sh "mv .${this.versionDefinitionFile}.tmp ${this.versionDefinitionFile}"
    }

    /**
     * Bump version up in {@link #versionDefinitionFile} after release publish.
     *
     * <p>If bump failed, like version is same as before update, a GradleException will be thrown.</p>
     *
     * @param version          new semantic version string. For example, {@code "1.2.3"}.
     */
    void bumpVersionAfterRelease(String version) throws GradleException {
        steps.sh "sed -e \"s#^version=.*\\\$#version=${version}#\" ${this.versionDefinitionFile} > .${this.versionDefinitionFile}.tmp"
        // compare if we successfully bumped the version
        String beforeConvert = steps.readFile "${this.versionDefinitionFile}"
        String afterConvert = steps.readFile ".${this.versionDefinitionFile}.tmp"
        log.finer("Before convert:\n${beforeConvert}")
        log.finer("After convert:\n${afterConvert}")
        if (beforeConvert == afterConvert) {
            throw new GradleException('Version bump is not successfully.')
        }

        // replace version
        steps.sh "mv .${this.versionDefinitionFile}.tmp ${this.versionDefinitionFile}"
    }

    /**
     * Declare a new version of gradle project.
     *
     * @Note This task will bump the package version on gradle settings, commit the change, and push
     * to GitHub. The commit is signed-off.
     *
     * @Example
     * <pre>
     *     def github = new org.zowe.jenkins_shared_library.scm.GitHub(this)
     *     // bump patch version on master branch
     *     gradle.version(
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
     * @param branch         which branch to perform the release
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
            newSemVer = Utils.interpretSemanticVersionBump(this.packageInfo['versionTrunks'], version)

            this.bumpVersionAfterRelease(newSemVer)
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
