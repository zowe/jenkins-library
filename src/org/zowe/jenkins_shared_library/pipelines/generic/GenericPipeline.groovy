/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.generic

// import com.cloudbees.groovy.cps.NonCPS
import groovy.util.logging.Log
import java.util.regex.Pattern
import org.zowe.jenkins_shared_library.artifact.JFrogArtifactory
import org.zowe.jenkins_shared_library.package.Pax
import org.zowe.jenkins_shared_library.pipelines.base.Branches
import org.zowe.jenkins_shared_library.pipelines.base.enums.ResultEnum
import org.zowe.jenkins_shared_library.pipelines.base.enums.StageStatus
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage
import org.zowe.jenkins_shared_library.pipelines.base.Pipeline
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.*
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.generic.models.*
import org.zowe.jenkins_shared_library.scm.GitHub
import org.zowe.jenkins_shared_library.scm.ScmException
import org.zowe.jenkins_shared_library.Utils

/**
 * Extends the functionality available in the {@link jenkins_shared_library.pipelines.base.Pipeline} class. This class adds methods for
 * building and testing your application.
 *
 * <p>A typical pipeline should include these stage in sequence:
 * <ul>
 * <li>build       : build and generate artifact locally.</li>
 * <li>test        : test the artifact.</li>
 * <li>sonarScan   : SonarQube static code scanning.</li>
 * <li>packaging   : package/create artifact.</li>
 * <li>publish     : publish the artifact to Artifactory. If the build is a release, we publish the artifact to release folder.</li>
 * <li>release     : if the build is a release, we tag the GitHub. If the build is a formal release, we also bump release on code base.</li>
 * </ul>
 * </p>
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link jenkins_shared_library.pipelines.base.Pipeline}</li>
 *     <li><a href="https://plugins.jenkins.io/credentials-binding">Credentials Binding</a></li>
 *     <li><a href="https://plugins.jenkins.io/junit">JUnit</a></li>
 *     <li><a href="https://plugins.jenkins.io/htmlpublisher">HTML Publisher</a></li>
 *     <li><a href="https://plugins.jenkins.io/cobertura">Cobertura</a></li>
 * </ul>
 * </dd></dl>
 *
 * @Example
 *
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline
 * node('pipeline-node') {
 *     GenericPipeline pipeline = new GenericPipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.admins.add("userid1", "userid2", "userid3")
 *
 *     // update branches settings if we have different settings from #defineDefaultBranches()
 *     pipeline.branches.addMap([
 *         [
 *             name         : 'lts-incremental',
 *             isProtected  : true,
 *             buildHistory : 20,
 *         ]
 *     ])
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup(
 *         // Define the git configuration
 *         github: [
 *             email: 'git-user-email@example.com',
 *             usernamePasswordCredential: 'git-user-credentials-id'
 *         ],
 *         // Define the artifactory configuration
 *         artifactory: [
 *             url : 'https://your-artifactory-url',
 *             usernamePasswordCredential : 'artifactory-credential-id',
 *         ]
 *     )
 *
 *     pipeline.build()   // Provide required parameters in your pipeline
 *     pipeline.test()    // Provide required parameters in your pipeline
 *     pipeline.publish() // Provide required parameters in your pipeline
 *     pipeline.release() // Provide required parameters in your pipeline
 *
 *     // MUST BE CALLED LAST
 *     pipeline.end()
 * }
 * </pre>
 */
@Log
class GenericPipeline extends Pipeline {
    /**
     * Build parameter name for "Perform Release"
     */
    protected static final String BUILD_PARAMETER_PERFORM_RELEASE = 'Perform Release'

    /**
     * Build parameter name for "Pre-Release String"
     */
    protected static final String BUILD_PARAMETER_PRE_RELEASE_STRING = 'Pre-Release String'

    /**
     * A map of branches.
     */
    Branches<GenericBranch> branches = new Branches<>(GenericBranch.class)

    /**
     * Temporary upload spec name
     *
     * @default {@code ".tmp-pipeline-publish-spec.json"}
     */
    protected static final String temporaryUploadSpecName = '.tmp-pipeline-publish-spec.json'

    /**
     * Stores the change information for reference later.
     */
    final ChangeInformation changeInfo

    /**
     * Package information extracted from project.
     *
     * <p>Based on type of project, the keys included could be vary.</p>
     *
     * @Note These keys should exist all the time, otherwise exceptions may be thrown for missing
     * values.
     * <ul>
     * <li>- name</li>
     * <li>- version</li>
     * <li>- versionTrunks</li>
     * </ul>
     */
    Map packageInfo

    /**
     * The full version pattern when the pipeline try to publish artifacts.
     *
     * @Note Allowed macros are same as macros defined in {@link #artifactoryUploadTargetPath}.
     *
     * @Default {@code "&#123;version&#125;&#123;prerelease&#125;&#123;branchtag&#125;&#123;buildnumber&#125;&#123;timestamp&#125;"}
     *
     * @Example With the default pattern, the pipeline may interpret the targe version string as
     * {@code "1.2.3-snapshot-23-20190101010101"}.
     */
    String publishTargetVersion = '{version}{prerelease}{branchtag}{buildnumber}{timestamp}'

    /**
     * Artifactory upload path pattern when the pipeline try to publish artifacts.
     *
     * @Note Allowed macros:
     * <ul>
     * <li>- repository: Artifactory repository name where the artifact will be published.</li>
     * <li>- package: value defined by pipeline.setPackage(name)</li>
     * <li>- subproject: optional value passed when parsing the path string</li>
     * <li>- version: the current version</li>
     * <li>- prerelease: pre-release string, like rc1, beta1, etc.</li>
     * <li>- branchtag: branch tag</li>
     * <li>- branchtag-uc: branch tag but in UPPER case.</li>
     * <li>- timestamp: timestamp in yyyyMMddHHmmss format</li>
     * <li>- buildnumber: current build number</li>
     * </ul>
     *
     * @Note If the artifactory repository is a Maven repository, the upload may fail if the
     * {@code branchtag} is lower case characters.
     *
     * @Default {@code "&#123;repository&#125;/&#123;package&#125;&#123;subproject&#125;/&#123;version&#125;&#123;branchtag-uc&#125;/"}
     *
     * @Example With the default pattern, the pipeline may interpret the targe upload path as
     * {@code "lib-snapshot-local/org/zowe/my-project/1.2.3-STAGINGS-TLS/"}.
     */
    String artifactoryUploadTargetPath = '{repository}/{package}{subproject}/{version}{branchtag-uc}/'

    /**
     * Artifactory file name pattern when the pipeline try to publish artifacts.
     *
     * @Note Allowed macros are same as macros defined in {@link #artifactoryUploadTargetPath} and with
     * these extras.
     * <ul>
     * <li>- filename: the base file name extracted from original artifact.</li>
     * <li>- fileext: the file extension extracted from original artifact.</li>
     * <li>- publishversion: parsed string of {@link #publishTargetVersion}</li>
     * </ul>
     * </p>
     *
     * @Default {@code "&#123;filename&#125;-&#123;publishversion&#125;&#123;fileext&#125;"}
     *
     * @Example With the default pattern, if we want to upload {@code my-artifact.zip}, the pipeline
     * may interpret the targe file name as {@code "my-artifact-1.2.3-snapshot-23-20190101010101.zip"}.
     */
    String artifactoryUploadTargetFile = '{filename}-{publishversion}{fileext}'

    /**
     * Holds junit test result files.
     *
     * <p>This is internal used to avoid publishing duplicated junit file to Jenkins.</p>
     */
    protected List<String> _junitResults = []

    /**
     * GitHub instance
     */
    GitHub github

    /**
     * JFrogArtifactory instance
     */
    JFrogArtifactory artifactory

    /**
     * PAX packaging instance
     */
    Pax pax

    /**
     * More control variables for the pipeline.
     */
    protected GenericPipelineControl _control = new GenericPipelineControl()

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the GenericPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pipeline = new GenericPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    GenericPipeline(steps) {
        super(steps)
        changeInfo = new ChangeInformation(steps)
        github = new GitHub(steps)
        artifactory = new JFrogArtifactory(steps)
        pax = new Pax(steps)
    }

    /**
     * Setup default branch settings
     */
    @Override
    protected void defineDefaultBranches() {
        this.branches.addMap([
            [
                name               : 'master',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                allowFormalRelease : true,
                releaseTag         : 'snapshot',
            ],
            [
                name               : 'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/master',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                allowFormalRelease : true,
                releaseTag         : '$1-snapshot',
            ],
            [
                name               : 'staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
            ],
            [
                name               : 'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
            ],
        ])
    }

    /**
     * If current pipeline branch can do a release.
     *
     * @Note For regular Pipeline build, since we cannot determin branch name easily, this method
     * will always return true to allow release.
     *
     * @param  branch     the branch name to check. By default, empty string will check current branch
     * @return               true or false
     */
    protected Boolean isReleaseBranch(String branch = '') {
        // use BRANCH_NAME as default value
        if (!branch && steps.env && steps.env.BRANCH_NAME) {
            branch = steps.env.BRANCH_NAME
        }
        // not a multibranch pipeline, always allow release?
        if (!steps.env || !steps.env.BRANCH_NAME) {
            return true
        }

        def result = false
        def branchProps = branches.getByPattern(branch)
        if (branchProps && branchProps.getAllowRelease()) {
            result = true
        }

        result
    }

    /**
     * If current pipeline branch can do a formal release
     *
     * @Note For regular Pipeline build, since we cannot determin branch name easily, this method
     * will always return true to allow formal release.
     *
     * @param  branch     the branch name to check. By default, empty string will check current branch
     * @return               true or false
     */
    protected Boolean isFormalReleaseBranch(String branch = '') {
        // use BRANCH_NAME as default value
        if (!branch && steps.env && steps.env.BRANCH_NAME) {
            branch = steps.env.BRANCH_NAME
        }
        // not a multibranch pipeline, always allow release?
        if (!steps.env || !steps.env.BRANCH_NAME) {
            return true
        }

        def result = false
        def branchProps = branches.getByPattern(branch)
        if (branchProps && branchProps.getAllowFormalRelease()) {
            result = true
        }

        result
    }

    /**
     * If current build is a release build based on the build parameter which starts the build.
     *
     * @return            true or false
     */
    protected Boolean isPerformingRelease() {
        if (steps && steps.params && steps.params[BUILD_PARAMETER_PERFORM_RELEASE]) {
            return true
        } else {
            return false
        }
    }

    /**
     * The pre-release string parameter of current build.
     *
     * @Note This value will be empty if the build is not a release build (build parameter
     * {@link #BUILD_PARAMETER_PERFORM_RELEASE} is not set).
     *
     * @return            the pre-release string, or empty if not set.
     */
    protected String getPreReleaseString() {
        if (steps && steps.params && steps.params[BUILD_PARAMETER_PERFORM_RELEASE] &&
            steps.params[BUILD_PARAMETER_PRE_RELEASE_STRING]) {
            return steps.params[BUILD_PARAMETER_PRE_RELEASE_STRING]
        } else {
            return ''
        }
    }

    /**
     * Get branch tag
     *
     * @param  branch     the branch name to check. By default, empty string will check current branch
     * @return            tag of the branch
     */
    protected String getBranchTag(String branch = '') {
        // use BRANCH_NAME as default value
        if (!branch && steps.env && steps.env.BRANCH_NAME) {
            branch = steps.env.BRANCH_NAME
        }

        String result = branch ?: Constants.DEFAULT_BRANCH_RELEASE_TAG

        if (branch) {
            def branchProps = branches.getByPattern(branch)
            if (branchProps) {
                def tag = branchProps.getReleaseTag()
                if (tag) { // has release tag defined
                    def replaced = branch.replaceAll(branchProps.name, tag)
                    if (branch != replaced) { // really replaced
                        result = replaced
                    }
                }
            }
        }

        return Utils.sanitizeBranchName(result)
    }

    /**
     * Return map of build string macros. Those macros will be used to parse build string.
     *
     * @param  macros        default value of macros.
     * @return               updated macro list.
     */
    protected Map<String, String> getBuildStringMacros(Map<String, String> macros = [:]) {
        Boolean _isReleaseBranch = this.isReleaseBranch()
        Boolean _isFormalReleaseBranch = this.isFormalReleaseBranch()
        Boolean _isPerformingRelease = this.isPerformingRelease()
        String _preReleaseString = this.getPreReleaseString()

        if (!macros.containsKey('repository')) {
            macros['repository'] = _isReleaseBranch && _isPerformingRelease ?
                                   JFrogArtifactory.REPOSITORY_RELEASE :
                                   JFrogArtifactory.REPOSITORY_SNAPSHOT
        }

        if (!macros.containsKey('package')) {
            macros['package'] = this.getPackageName().replace('.', '/')
        }
        if (!macros['package']) {
            throw new PublishStageException('Cannot determin package name for build string', '-')
        }
        if (!macros.containsKey('subproject')) {
            macros['subproject'] = ''
        }
        if (!macros.containsKey('version')) {
            macros['version'] = this.getVersion()
        }
        if (!macros['version']) {
            throw new PublishStageException('Cannot determin version for build string', '-')
        }
        if (_isReleaseBranch && _isPerformingRelease) {
            if (!macros.containsKey('prerelease')) {
                macros['prerelease'] = _preReleaseString
            }
            if (!macros.containsKey('branchtag')) {
                macros['branchtag'] = ''
            }
            if (!macros.containsKey('timestamp')) {
                macros['timestamp'] = ''
            }
            if (!macros.containsKey('buildnumber')) {
                macros['buildnumber'] = ''
            }
        } else {
            if (!macros.containsKey('prerelease')) {
                macros['prerelease'] = ''
            }
            if (!macros.containsKey('branchtag')) {
                macros['branchtag'] = this.getBranchTag()
            }
            if (!macros.containsKey('timestamp')) {
                macros['timestamp'] = Utils.getTimestamp()
            }
            if (!macros.containsKey('buildnumber')) {
                macros['buildnumber'] = "${(steps.env && steps.env.BUILD_NUMBER) ?: ''}"
            }
        }

        // normalize some values
        if (macros['subproject'] && !macros['subproject'].startsWith('/')) {
            macros['subproject'] = '/' + macros['subproject']
        }
        if (macros['prerelease'] && !macros['prerelease'].startsWith('-')) {
            macros['prerelease'] = '-' + macros['prerelease']
        }
        if (macros['branchtag'] && !macros['branchtag'].startsWith('-')) {
            macros['branchtag'] = '-' + macros['branchtag']
        }
        if (macros['timestamp'] && !macros['timestamp'].startsWith('-')) {
            macros['timestamp'] = '-' + macros['timestamp']
        }
        if (macros['buildnumber'] && !macros['buildnumber'].startsWith('-')) {
            macros['buildnumber'] = '-' + macros['buildnumber']
        }

        if (!macros.containsKey('publishversion')) {
            macros['publishversion'] = _parseString(this.publishTargetVersion, macros)
        }

        macros['branchtag-uc'] = macros['branchtag'] ? macros['branchtag'].toUpperCase() : ''

        log.fine("getBuildStringMacros macros: ${macros}")

        return macros
    }

    /**
     * Extract macro of "filename" and "fileext" for artifactory upload file.
     *
     * @Note The {@code filename} and {@code fileext} extracted from the file path does not include
     * path to the file and version information.
     *
     * <p>For example, if we have a local artifact {@code "./path/to/my-artifact-1.2.3-snapshot.zip"}, then
     * the expected macros extracted are: {@code [filename: "my-artifact", fileext: "zip"]}</p>
     *
     * @param  file     original file name
     * @return          macro map
     */
    protected Map<String, String> extractArtifactoryUploadTargetFileMacros(String file) {
        Map<String, String> macros = ['filename': '', 'fileext': '']

        String baseName = file.lastIndexOf('/').with {
            it != -1 ? file[(it + 1)..-1] : file
        }
        Integer idx = baseName.lastIndexOf('.')
        if (idx != -1) {
            macros['filename'] = baseName[0..(idx - 1)]
            macros['fileext'] = baseName[idx..-1]
        } else {
            macros['filename'] = baseName
            macros['fileext'] = ''
        }

        // Does file name looks like my-project-1.2.3-snapshot? If so, we remove the version information.
        def matches = macros['filename'] =~ /^(.+)-([0-9]+\.[0-9]+\.[0-9]+)(-[0-9a-zA-Z-+\.]+)?$/
        if (matches.matches() && matches[0] && matches[0].size() == 4) {
            if (this.packageInfo && this.packageInfo['versionTrunks']) {
                String semver = "${this.packageInfo['versionTrunks']['major']}.${this.packageInfo['versionTrunks']['minor']}.${this.packageInfo['versionTrunks']['patch']}"
                if (matches[0][2] == semver) {
                    // the artifact file name has version infromation
                    log.finer "Version in artifact \"${macros['filename']}\" name is extracted as \"${matches[0][1]}\"."
                    macros['filename'] = matches[0][1]
                }
            }
        }

        return macros
    }

    /**
     * Parse a string using macros Map.
     *
     * <p>Macros wrap with curly brackets will be replace in the string. For example, all occurence
     * of {@code &#123;repository&#125;} in the string will be replaced with value of macro key
     * {@code repository}.</p>
     *
     * @param  str        string to parse
     * @param  macros     map of macros to replace
     * @return            parsed string
     */
    protected String _parseString(String str, Map<String, String> macros) {
        for (String m : macros) {
            str = str.replace("{${m.key}}", m.value)
        }

        return str
    }

    /**
     * Calls {@link jenkins_shared_library.pipelines.base.Pipeline#setupBase(jenkins_shared_library.pipelines.base.arguments.SetupArguments)} to setup the build.
     *
     * @Stages
     * This method adds 2 stages to the build:
     *
     * <dl>
     *     <dt><b>Check for CI Skip</b></dt>
     *     <dd>
     *         Checks that the build commit doesn't contain the CI Skip {@link jenkins_shared_library.pipelines.Constants#CI_SKIP} indicator. If the pipeline finds
     *         the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     *         will also be skipped. The build will also be marked as not built in this scenario.
     *     </dd>
     *     <dt><b>Init Generic Pipeline</b></dt>
     *     <dd>
     *         This initialization stage will run {@code #init()} methods of dependended instances,
     *         for example, GitHub, JFrogArtifactory, Pax etc. This step is placed in a stage because
     *         some initlialization requires code checkout. You can specify {@link jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments#extraInit} to extend the
     *         default initialization.
     *     </dd>
     * </dl>
     *
     * @Note This method was intended to be called {@code setup} but had to be named
     * {@code setupGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     */
    void setupGeneric(GenericSetupArguments arguments) {
        // Call setup from the super class
        super.setupBase(arguments)

        // prepare default configurations
        this.defineDefaultBranches()

        createStage(name: 'Check for CI Skip', stage: {
            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: "git log -1 | grep '.*\\[ci skip\\].*'"
            if (result == 0) {
                steps.echo "\"${Constants.CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(ResultEnum.NOT_BUILT)
            }
        }, timeout: arguments.ciSkip)

        createStage(name: 'Init Generic Pipeline', stage: {
            if (arguments.github) {
                this.steps.echo "Init github configurations ..."
                this.github.init(arguments.github)
            }
            if (arguments.artifactory) {
                this.steps.echo "Init artifactory configurations ..."
                this.artifactory.init(arguments.artifactory)
            }
            if (arguments.pax) {
                this.steps.echo "Init pax packaging server configurations ..."
                this.pax.init(arguments.pax)
            }

            if (arguments.extraInit) {
                this.steps.echo "Run extra initialization ..."
                arguments.extraInit(this)
            }
        }, timeout: arguments.initForGeneric)
    }

    /**
     * Initialize the pipeline.
     *
     * @param arguments A map that can be instantiated as {@link jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments}
     * @see #setupGeneric(GenericSetupArguments)
     */
    void setupGeneric(Map arguments = [:]) {
        setupGeneric(arguments as GenericSetupArguments)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     *
     * @param arguments A map that can be instantiated as {@link jenkins_shared_library.pipelines.generic.arguments.GenericSetupArguments}
     */
    @Override
    protected void setup(Map arguments = [:]) {
        setupGeneric(arguments)
    }

    /**
     * Signal that no more stages will be added and begin pipeline execution.
     *
     * @param options Options to send to {@link jenkins_shared_library.pipelines.base.Pipeline#endBase(java.util.Map)}
     */
    void endGeneric(Map options = [:]) {
        // can we do a release? if so, allow a release parameter
        if (isReleaseBranch()) {
            this.addBuildParameter(steps.booleanParam(
                name         : BUILD_PARAMETER_PERFORM_RELEASE,
                description  : 'Perform a release of the project. A release will lead to a GitHub tag be created. After a formal release (which doesn\'t have pre-release string), your branch release will be bumped a PATCH level up. By default, release can only be enabled on branches which "allowRelease" is true.',
                defaultValue : false
            ))
            this.addBuildParameter(steps.string(
                name         : BUILD_PARAMETER_PRE_RELEASE_STRING,
                description  : 'Pre-release string for a release. For example: rc.1, beta.1, etc. This is required if the release is not performed on branches which "allowFormalRelease" is true.',
                defaultValue : '',
                trim         : true
            ))
        }

        super.endBase(options)
    }

    /**
     * Pseudo end method, should be overridden by inherited classes
     * @param args A map that can be instantiated as {@link jenkins_shared_library.pipelines.base.arguments.EndArguments}.
     */
    @Override
    protected void end(Map args = [:]) {
        endGeneric(args)
    }

    /**
     * Creates a stage that will build a generic package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} class. The
     * {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Build: {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments#name}</b></dt>
     *     <dd>
     *         Runs the build of your application. The build stage also ignores any
     *         {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments#resultThreshold} provided and only runs
     *         on {@link ResultEnum#SUCCESS}.</p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions can be thrown by the build stage:
     *
     *     <dl>
     *         <dt><b>{@link jenkins_shared_library.pipelines.generic.exceptions.BuildStageException}</b></dt>
     *         <dd>When arguments.stage is provided. This is an invalid argument field for the operation.</dd>
     *         <dd>When called more than once in your pipeline. Only one build may be present in a
     *             pipeline.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When arguments.operation is not provided.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code build} but had to be named
     * {@code buildGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void buildGeneric(Map arguments = [:]) {
        BuildStageException preSetupException

        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        BuildStageArguments args = arguments

        if (_control.build) {
            preSetupException = new BuildStageException("Only one build step is allowed per pipeline.", args.name)
        } else if (args.stage) {
            preSetupException = new BuildStageException("arguments.stage is an invalid option for buildGeneric", args.name)
        }

        args.name = "Build: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            args.operation(stageName)
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage build = createStage(args)
        if (!_control.build) {
            _control.build = build
        }
    }

    /**
     * Pseudo build method, should be overridden by inherited classes
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    protected void build(Map arguments = [:]) {
        buildGeneric(arguments)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} class.</p>
     *
     * @Stages
     * This method adds the following stage to the build:
     *
     * <dl>
     *     <dt><b>Test: {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#name}</b></dt>
     *     <dd>
     *         <p>Runs one of your application tests. If the test operation throws an error, that error is
     *         ignored and  will be assumed to be caught in the junit processing. Some test functions may
     *         exit with a non-zero return code on a test failure but may still capture junit output. In
     *         this scenario, it is assumed that the junit report is either missing or contains failing
     *         tests. In the case that it is missing, the build will fail on this report and relevant
     *         exceptions are printed. If the junit report contains failing tests, the build will be marked
     *         as unstable and a report of failing tests can be viewed.</p>
     *
     *         <p>The following reports can be captured:</p>
     *         <dl>
     *             <dt><b>Test Results HTML Report</b></dt>
     *             <dd>
     *                 This is an html report that contains the result of the build. The report must be defined to
     *                 the method in the {@link TestStageArguments#htmlReports} variable.
     *             </dd>
     *             <dt><b>Code Coverage HTML Report</b></dt>
     *             <dd>
     *                 This is an HTML report generated from code coverage output from your build. The report can
     *                 be omitted by omitting {@link TestStageArguments#htmlReports}
     *             </dd>
     *             <dt><b>JUnit Report</b></dt>
     *             <dd>
     *                 This report feeds Jenkins the data about the current test run. It can be used to mark a build
     *                 as failed or unstable. The report location must be present in
     *                 {@link TestStageArguments#junit}
     *             </dd>
     *             <dt><b>Cobertura Report</b></dt>
     *             <dd>
     *                 This report feeds Jenkins the data about the coverage results for the current test run. If
     *                 no Cobertura options are passed, then no coverage data will be collected. For more
     *                 information, see {@link TestStageArguments#cobertura}
     *             </dd>
     *         </dl>
     *
     *         <p>
     *             The test stage will execute by default if the current build result is greater than or
     *             equal to {@link ResultEnum#UNSTABLE}. If a different status is passed, that will take
     *             precedent.
     *         </p>
     *
     *         <p>
     *             After the test is complete, the stage will continue to collect the JUnit Report and the Test
     *             Results HTML Report. The stage will fail if either of those are missing. If specified, the
     *             Cobertura Report are then captured. The build will fail if these reports are to be collected
     *             and were missing.
     *         </p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     * <p>
     *     The test stage can throw the following exceptions:
     *
     *     <dl>
     *         <dt><b>{@link TestStageException}</b></dt>
     *         <dd>When a test stage is created before a call to {@link #buildGeneric(Map)}</dd>
     *         <dd>When {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#junit} is missing</dd>
     *         <dd>When invalid options are specified for {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#junit}</dd>
     *         <dd>When {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#cobertura} is provided but has an invalid format</dd>
     *         <dd>When {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#operation} is missing.</dd>
     *         <dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code test} but had to be named
     * {@code testGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    void testGeneric(Map arguments = [:]) {
        // Default the resultThreshold to unstable for tests,
        // if a custom value is passed then that will be used instead
        if (!arguments.resultThreshold) {
            arguments.resultThreshold = ResultEnum.UNSTABLE
        }

        TestStageArguments args = arguments

        TestStageException preSetupException

        if (args.stage) {
            preSetupException = new TestStageException("arguments.stage is an invalid option for testGeneric", args.name)
        }

        args.name = "Test: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            } else if (_control.build?.status != StageStatus.SUCCESS) {
                throw new TestStageException("Tests cannot be run before the build has completed", args.name)
            }

            steps.echo "Processing Arguments"

            if (!args.junit) {
                throw new TestStageException("JUnit Report not provided", args.name)
            }

            for (def rep : args.htmlReports) {
                TestReport report = rep
                _validateReportInfo(report, "Test Results HTML Report", args.name)
            }

            if (!args.operation) {
                throw new PublishStageException("Missing test operation!", args.name)
            }

            try {
                args.operation(stageName)
            } catch (Exception exception) {
                // If the script exited with code 143, that indicates a SIGTERM event was
                // captured. If this is the case then the process was killed by Jenkins.
                if (exception.message == "script returned exit code 143") {
                    throw exception
                }

                steps.echo "Exception: ${exception.message}"
            }

            // Collect junit report
            if (args.junit) {
                log.finer "junit arguments: ${args.junit}"
                def files = steps.findFiles glob: args.junit
                files.each { f ->
                    String file = f.toString()

                    if (this._junitResults.contains(file)) {
                        log.finer("- found junit file: ${file}")
                        this._junitResults.push(file)
                        steps.echo "Publishing junit file ${file}"
                        steps.junit file
                    }
                }
            }

            // Collect cobertura coverage if specified
            if (args.cobertura) {
                steps.cobertura(TestStageArguments.coberturaDefaults + args.cobertura)
            }

            // publish html reports if specified
            for (TestReport report : args.htmlReports) {
                // Collect Test Results HTML Report
                steps.publishHTML(target: [
                        allowMissing          : false,
                        alwaysLinkToLastBuild : true,
                        keepAll               : true,
                        reportDir             : report.dir,
                        reportFiles           : report.files,
                        reportName            : report.name
                ])
            }
        }

        // Create the stage and ensure that the tests are properly added.
        Stage test = createStage(args)
        if (!(_control.release || _control.publish)) {
            _control.prePublishTests += test
        }
    }

    /**
     * Pseudo test method, should be overridden by inherited classes
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    protected void test(Map arguments = [:]) {
        testGeneric(arguments)
    }

    /**
     * Creates a stage that will perform SonarQube static code scanning.
     *
     * <p>The default behavior of this stage will check if there is a {@code "sonar-project.properties"}
     * file presents. If so, then run a SonarQube static code scanning using that configuration file.</p>
     *
     * <p>Provide arguments.operation to override the default behavior.</p>
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments} class. The
     * {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>SonarQube Scan: {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments#name}</b></dt>
     *     <dd>
     *         Perform a SonarQube static code scanning on your application. The stage also ignores any
     *         {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments#resultThreshold} provided and only runs
     *         on {@link ResultEnum#SUCCESS}.</p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions can be thrown by the sonar scan stage:
     *
     *     <dl>
     *         <dt><b>{@link jenkins_shared_library.pipelines.generic.exceptions.SonarScanStageException}</b></dt>
     *         <dd>When arguments.stage is provided. This is an invalid argument field for the operation.</dd>
     *         <dd>When called more than once in your pipeline. Only one sonar scan may be present in a
     *             pipeline.</dd>
     *         <dd>When arguments.scannerServer is not defined and arguments.operation is not provided.</dd>
     *         <dd>When arguments.scannerTool is not defined and arguments.operation is not provided.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When arguments.operation is not provided.</dd>
     *     </dl>
     * </p>
     *
     * @Note With default behavior (by not providing arguments.operation), the SonarQube scan stage
     * will only run if {@code "sonar-project.properties"} file presents. This file should have
     * basic scanning configurations.
     *
     * @Note With default behavior (by not providing arguments.operation), the SonarQube scan stage
     * won't fail the build if the scan result doesn't pass the threshold. The stage will only fail
     * when then scanning cannot be performed, like cannot connect to the SonarQube server.
     *
     * @Note This method was intended to be called {@code sonarScan} but had to be named
     * {@code sonarScanGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments} used to define
     *                  the stage.
     */
    void sonarScanGeneric(Map arguments = [:]) {
        if (!arguments) {
            // can be empty
            arguments = [:]
        }

        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        SonarScanStageArguments args = arguments as SonarScanStageArguments

        SonarScanStageException preSetupException

        if (args.stage) {
            preSetupException = new SonarScanStageException("arguments.stage is an invalid option for sonarScanGeneric", args.name)
        }

        args.name = "SonarQube Scan${arguments.name ? ": ${arguments.name}" : ""}"

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            // execute operation Closure if provided
            if (args.operation) {
                args.operation(stageName)
            } else {
                if (!args.scannerServer) {
                    throw new SonarScanStageException("arguments.scannerServer is not defined for sonarScanGeneric", args.name)
                }
                // scannerTool is required for default operation
                if (!args.scannerTool) {
                    throw new SonarScanStageException("arguments.scannerTool is not defined for sonarScanGeneric", args.name)
                }

                def configExists = steps.fileExists('sonar-project.properties')
                if (configExists) {
                    steps.echo 'Found sonar-project.properties, will perform SonarQube scanning ...'
                    def scannerHome = this.steps.tool args.scannerTool
                    this.steps.withSonarQubeEnv(args.scannerServer) {
                        this.steps.sh "${scannerHome}/bin/sonar-scanner"
                    }
                } else {
                    steps.echo 'Not found sonar-project.properties, no SonarQube scan performed.'
                }
            }
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage sonarScan = createStage(args)
        if (!_control.sonarScan) {
            _control.sonarScan = sonarScan
        }
    }

    /**
     * Pseudo SonarQube Scan method, should be overridden by inherited classes
     *
     * @param arguments The arguments for the sonarScan step.
     */
    protected void sonarScan(Map arguments = [:]) {
        sonarScanGeneric(arguments)
    }

    /**
     * Creates a stage that will package artifact(s).
     *
     * <p>The default behavior of this stage will check if there is a local PAX workspace presents.
     * If so, then run a PAX packaging process to generate a Pax package.</p>
     *
     * <p>Provide arguments.operation to override the default behavior.</p>
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments} class. The
     * {@link jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Packaging: {@link jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments#name}</b></dt>
     *     <dd>
     *         Create a package for your project. The stage also ignores any
     *         {@link jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments#resultThreshold} provided and only runs
     *         on {@link ResultEnum#SUCCESS}.</p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions can be thrown by the sonar scan stage:
     *
     *     <dl>
     *         <dt><b>{@link jenkins_shared_library.pipelines.generic.exceptions.PackagingStageException}</b></dt>
     *         <dd>When arguments.stage is provided. This is an invalid argument field for the operation.</dd>
     *         <dd>When called more than once in your pipeline. Only one sonar scan may be present in a
     *             pipeline.</dd>
     *         <dd>When arguments.name is not defined. This name will be used as final pacakge name.</dd>
     *         <dd>When arguments.sshHost is not defined and arguments.operation is not provided.</dd>
     *         <dd>When arguments.sshCredential is not defined and arguments.operation is not provided.</dd>
     *         <dd>When arguments.remoteWorkspace is not defined and arguments.operation is not provided.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When arguments.operation is not provided.</dd>
     *     </dl>
     * </p>
     *
     * @Note With default behavior (by not providing arguments.operation), the SonarQube scan stage
     * won't fail the build if the scan result doesn't pass the threshold. The stage will only fail
     * when then scanning cannot be performed, like cannot connect to the SonarQube server.
     *
     * @Note This method was intended to be called {@code pacakging} but had to be named
     * {@code packagingGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link jenkins_shared_library.pipelines.generic.arguments.SonarScanStageArguments} used to define
     *                  the stage.
     */
    void packagingGeneric(Map arguments = [:]) {
        if (!arguments) {
            // can be empty
            arguments = [:]
        }

        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        PackagingStageArguments args = arguments as PackagingStageArguments

        PackagingStageException preSetupException

        if (args.stage) {
            preSetupException = new PackagingStageException("arguments.stage is an invalid option for packagingGeneric", args.name)
        }
        if (!args.name) {
            // try a default value from package info
            if (this.packageInfo && this.packageInfo['name']) {
                args.name = this.packageInfo['name']
            }
        }
        if (!args.name) {
            preSetupException = new PackagingStageException("arguments.name is not defined for packagingGeneric", args.name)
        }
        // localWorkspace should have a default value after init stage.
        // if (!args.localWorkspace) {
        //     preSetupException = new PackagingStageException("arguments.localWorkspace is not defined for packagingGeneric", args.name)
        // }

        def originalPackageName = args.name
        // now args.name is used as stage name
        args.name = "Packaging: ${args.name}"

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            // execute operation Closure if provided
            if (args.operation) {
                args.operation(stageName)
            } else {
                // re-init if there are config changes passed on arguments
                this.pax.init(arguments)
                def workspaceExists = steps.fileExists(this.pax.localWorkspace)
                if (workspaceExists) {
                    steps.echo "Found local packaging workspace ${this.pax.localWorkspace}"

                    if (!this.pax.getSshHost()) {
                        throw new PackagingStageException("PAX server configuration sshHost is missing", this.pax.sshHost)
                    }
                    if (!this.pax.getSshCredential()) {
                        throw new PackagingStageException("PAX server configuration sshCredential is missing", this.pax.sshCredential)
                    }
                    if (!this.pax.getRemoteWorkspace()) {
                        throw new PackagingStageException("PAX server configuration remoteWorkspace is missing", this.pax.remoteWorkspace)
                    }

                    // normalize package name
                    def paxPackageName = Utils.sanitizeBranchName(originalPackageName)
                    steps.echo "Creating pax file \"${paxPackageName}\" from workspace..."
                    def result = this.pax.pack(
                        job             : "pax-packaging-${paxPackageName}",
                        filename        : "${paxPackageName}.pax",
                        paxOptions      : args.paxOptions ?: '',
                    )
                    if (steps.fileExists("${this.pax.localWorkspace}/${paxPackageName}.pax")) {
                        steps.echo "Packaging result ${paxPackageName}.pax is in place."
                    } else {
                        steps.sh "ls -la ${this.pax.localWorkspace}"
                        steps.error "Failed to find packaging result ${paxPackageName}.pax"
                    }
                } else {
                    steps.echo "Not found local packaging workspace ${this.pax.localWorkspace}"
                }
            }
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage packaging = createStage(args)
        if (!_control.packaging) {
            _control.packaging = packaging
        }
    }

    /**
     * Pseudo packaging method, should be overridden by inherited classes
     * @param arguments The arguments for the packaging step. {@code arguments.name} must be
     *                        provided.
     */
    protected void packaging(Map arguments) {
        packagingGeneric(arguments)
    }

    /**
     * Creates a stage that will publish artifacts to Artifactory.
     *
     * <p>By default, if you publish a pre-release version on formal release branch, the pipeline will
     * show a confirmation information requiring human intervene. For example, you want to publish
     * {@code rc1} on {@code master} branch. Normally a pre-release should be released from {@code staging}
     * branch. Set {@link PublishStageArguments.allowPublishPreReleaseFromFormalReleaseBranch}
     * to true to disable it.</p>
     *
     * @Conditions
     *
     * <p>
     *     The stage will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is
     *         {@link ResultEnum#SUCCESS} or higher.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     The Publish stage will throw the following exceptions:
     *
     *     <dl>
     *         <dt><b>{@link PublishStageException}</b></dt>
     *         <dd>When stage is provided as an argument. This is an invalid parameter for both
     *             stages</dd>
     *         <dd>When a test stage has not executed. This prevents untested code from being
     *             published</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code publish} but had to be named
     * {@code publishGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments The arguments for the publish step.
     */
    void publishGeneric(Map arguments) {
        if (!arguments) {
            // can be empty
            arguments = [:]
        }

        arguments.resultThreshold = ResultEnum.SUCCESS

        PublishStageArguments args = arguments as PublishStageArguments

        args.name = "Publish${arguments.name ? ": ${arguments.name}" : ""}"

        PublishStageException preSetupException

        if (args.stage) {
            preSetupException = new PublishStageException("arguments.stage is an invalid option for publishGeneric", args.name)
        }

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (_control.build?.status != StageStatus.SUCCESS) {
                throw new PublishStageException("Build must be successful to publish", args.name)
            } else if (_control.prePublishTests && _control.prePublishTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new PublishStageException("All test stages before publish must be successful or skipped!", args.name)
            } else if (_control.prePublishTests.size() == 0) {
                throw new PublishStageException("At least one test stage must be defined", args.name)
            }
            Boolean _isReleaseBranch = this.isReleaseBranch()
            Boolean _isFormalReleaseBranch = this.isFormalReleaseBranch()
            Boolean _isPerformingRelease = this.isPerformingRelease()
            String _preReleaseString = this.getPreReleaseString()

            if (_isPerformingRelease) {
                // release related validations
                if (!_isReleaseBranch) {
                    throw new PublishStageException("Cannot perform publish/release on non-release branch", args.name)
                }
                if (_isFormalReleaseBranch && !_isReleaseBranch) {
                    throw new PublishStageException("Cannot perform formal release on non-release branch", args.name)
                }
                if (_isReleaseBranch && !_isFormalReleaseBranch && !_preReleaseString) {
                    throw new PublishStageException("Pre-release string is required to perform a non-formal-release", args.name)
                }
                if (_isReleaseBranch && _isFormalReleaseBranch && _preReleaseString &&
                    !args.allowPublishPreReleaseFromFormalReleaseBranch) {
                    // performing pre-release on formal release branch require human intervene
                    Map action = Utils.waitForInput(
                        this.steps,
                        [
                            timeout: [time: 30, unit: 'MINUTES'],
                            message: "You choose to release on a formal release branch with pre-release string, this may cause potential tag mismatch issues. Please confirm you want to proceed:"
                        ]
                    )
                    if (!action['proceed']) {
                        this.steps.error "Pipeline aborted by ${action['user']}"
                    }
                }
            }

            // store the publish version
            Map<String, String> macros = getBuildStringMacros()
            steps.env['PUBLISH_VERSION'] = macros['publishversion']

            if (_isPerformingRelease) {
                String tag = 'v' + steps.env['PUBLISH_VERSION']
                if (this.github.tagExistsRemote(tag)) {
                    throw new PublishStageException("Github tag \"${tag}\" already exists, publish abandoned.", args.name)
                }
            }

            // execute operation Closure if provided
            if (args.operation) {
                args.operation(stageName)
            }

            // upload artifacts if provided
            if (args.artifacts && args.artifacts.size() > 0) {
                def baseTargetPath = args.publishTargetPath ?: artifactoryUploadTargetPath
                this.uploadArtifacts(args.artifacts, baseTargetPath)
            } else {
                steps.echo "No artifacts to publish."
            }
        }

        Stage publish = createStage(args)
        if (!_control.publish) {
            _control.publish = publish
        }
    }

    /**
     * Upload artifacts.
     *
     * <p>This is a part of publish stage default behavior. If {@link PublishStageArguments#artifacts}
     * is defined, those artifacts will be uploaded to artifactory with this method.</p>
     *
     * @param artifacts      list of artifacts. glob file pattern is allowed.
     * @param baseTargetPath The targe path to upload
     */
    protected void uploadArtifacts(List<String> artifacts, String baseTargetPath) {
        if (!baseTargetPath.endsWith('/')) {
            baseTargetPath += '/'
        }

        log.fine("Uploading artifacts ${artifacts} to ${baseTargetPath}")
        Map uploadSpec = steps.readJSON text: '{"files":[]}'
        Map<String, String> baseMacros = getBuildStringMacros()
        artifacts.each { artifact ->
            log.fine("- pattern ${artifact}")
            def files = steps.findFiles glob: artifact
            files.each { file ->
                String f = file.toString()
                String targetFileFull = baseTargetPath + artifactoryUploadTargetFile
                Map<String, String> fileMacros = extractArtifactoryUploadTargetFileMacros(f)
                Map<String, String> macros = baseMacros.clone() + fileMacros
                String t = _parseString(targetFileFull, macros)
                log.fine("- + found ${f} -> ${t}")
                uploadSpec['files'].push([
                    "pattern" : f,
                    "target"  : t
                ])
            }
        }

        log.fine("Spec of uploading artifact: ${uploadSpec}")

        steps.writeJSON file: temporaryUploadSpecName, json: uploadSpec
        artifactory.upload(spec: temporaryUploadSpecName)
    }

    /**
     * Pseudo publish method, should be overridden by inherited classes
     * @param arguments The arguments for the publish step.
     */
    protected void publish(Map arguments) {
        publishGeneric(arguments)
    }

    /**
     * Creates a stage that will execute a release
     *
     * @see #releaseGeneric(ReleaseStageArguments)
     */
    void releaseGeneric(Map arguments = [:]) {
        if (!arguments) {
            // can be empty
            arguments = [:]
        }

        ReleaseStageArguments args = arguments as ReleaseStageArguments

        this.releaseGeneric(args)
    }

    /**
     * Creates a stage that will execute a release
     *
     * <p>The default behavior of release stage is after the publish stage, which release artifacts
     * have been uploaded to Artifactory, we have to tag the GitHub branch with the release. If this
     * is a formal release, we also need to bump project version, so we won't build same version
     * again. The version bump will create a commit and push to GitHub. After all, the stage will
     * send out email notification for the new release.</p>
     *
     * <p>Provide arguments.operation to override the default behavior. Or you can provide
     * arguments.tagBranch or arguments.bumpVersion to override part of the behavior.</p>
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link ReleaseStageArguments} class. The
     * {@link ReleaseStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Releasing: {@link ReleaseStageArguments#name}</b></dt>
     *     <dd>This stage is responsible for tagging the branch and bumping the release of your
     *     application source.</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     This stage will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is {@link ResultEnum#SUCCESS} or higher.</li>
     *         <li>The stage will only execute if the current branch is a releasing branch.</li>
     *         <li>The build job is started when build parameter "Perform Release" {@link #BUILD_PARAMETER_PERFORM_RELEASE} is checked.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions will be thrown if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link ReleaseStageException}</b></dt>
     *         <dd>When stage is provided as an argument.</dd>
     *         <dd>When publish stage is not successful.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code release} but had to be named
     * {@code releaseGeneric} due to the issues described in {@link jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link ReleaseStageArguments} used to define the stage.
     */
    void releaseGeneric(ReleaseStageArguments arguments) {
        ReleaseStageException preSetupException

        if (arguments.stage) {
            preSetupException = new ReleaseStageException("arguments.stage is an invalid option for releaseGeneric", arguments.name)
        }

        arguments.name = "Releasing${arguments.name ? ": ${arguments.name}" : ""}"

        // extra shouldExecute check if we are doing release
        def shouldExecuteClone
        if (arguments.shouldExecute) {
            shouldExecuteClone = arguments.shouldExecute.clone()
        }
        arguments.shouldExecute = {
            boolean shouldExecute = this.isPerformingRelease() && this.isReleaseBranch()

            if (shouldExecuteClone && shouldExecute) {
                shouldExecute = shouldExecuteClone()
            }

            return shouldExecute
        }

        arguments.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            // no need for other checks because we require publish stage to be success
            if (_control.publish?.status != StageStatus.SUCCESS) {
                throw new ReleaseStageException("Publish must be successful to release", arguments.name)
            }

            // execute operation Closure if provided
            if (arguments.operation) {
                arguments.operation(stageName)
            } else {
                // this is the default release behaviors
                if (arguments.tagBranch) {
                    arguments.tagBranch()
                } else {
                    this.tagBranch()
                }

                // only bump version on formal release without pre-release string
                if (this.isFormalReleaseBranch() && this.getPreReleaseString() == '') {
                    if (arguments.bumpVersion) {
                        arguments.bumpVersion()
                    } else {
                        this.bumpVersion()
                    }
                } else {
                    this.steps.echo "No need to bump version."
                }

                // send out notice
                this.sendReleaseNotice()
            }
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage release = createStage(arguments)
        if (!_control.release) {
            _control.release = release
        }
    }

    /**
     * Tag branch when release.
     *
     * @Example If we are releasing {@code "1.2.3"} with pre-release string {@code "rc1"}, we will
     * creating a tag {@code "v1.2.3-rc1"}.
     */
    protected void tagBranch() {
        String _preReleaseString = this.getPreReleaseString()

        // should be able to guess repository and branch name
        this.github.initFromFolder()
        if (!this.github.repository) {
            throw new ScmException('Github repository is not defined and cannot be determined.')
        }
        String tag = 'v' + steps.env['PUBLISH_VERSION']
        this.steps.echo "Creating tag \"${tag}\" at \"${this.github.repository}:${this.github.branch}\"..."

        this.github.tag(tag: tag)
    }

    /**
     * This method should be overridden to properly bump version in different kind of project.
     *
     * <p>For example, npm package should use `npm version patch` to bump, and gradle project should
     * update the {@code version} definition in {@code "gradle.properties"}.</p>
     */
    protected void bumpVersion() {
        log.warning('This method should be overridden.')
    }

    /**
     * Send out email notification for the new version released.
     */
    protected void sendReleaseNotice() {
        String subject = 'NEW_RELEASE'
        String bodyText = """<h3>${steps.env.JOB_NAME}</h3>
<p>Branch: <b>${steps.env.BRANCH_NAME ?: '-'}</b></p>
<p>Deployed Package: <b>${this.getPackageName()}</b></p>
<p>Version: <b>v${steps.env['PUBLISH_VERSION']}</b></p>
"""

        this._email.send(
            subjectTag: subject,
            body: bodyText,
            to: admins.getEmailList()
        )
    }

    /**
     * Pseudo release method, should be overridden by inherited classes
     * @param arguments The arguments for the release step.
     */
    protected void release(Map arguments = [:]) {
        releaseGeneric(arguments)
    }

    /**
     * Validates that a test report has the required options.
     *
     * @param report The report to validate
     * @param reportName The name of the report being validated
     * @param stageName The name of the stage that is executing.
     *
     * @throw {@link TestStageException} when any of the report properties are invalid.
     */
    protected static void _validateReportInfo(TestReport report, String reportName, String stageName) {
        if (!report.dir) {
            throw new TestStageException("${reportName} is missing property `dir`", stageName)
        }

        if (!report.files) {
            throw new TestStageException("${reportName} is missing property `files`", stageName)
        }

        if (!report.name) {
            throw new TestStageException("${reportName} is missing property `name`", stageName)
        }
    }
}
