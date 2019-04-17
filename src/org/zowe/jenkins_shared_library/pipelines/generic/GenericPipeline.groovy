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
import org.zowe.jenkins_shared_library.pipelines.base.enums.ResultEnum
import org.zowe.jenkins_shared_library.pipelines.base.enums.StageStatus
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage
import org.zowe.jenkins_shared_library.pipelines.base.Pipeline
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.*
import org.zowe.jenkins_shared_library.pipelines.generic.enums.BuildType
import org.zowe.jenkins_shared_library.pipelines.generic.enums.GitOperation
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.git.*
import org.zowe.jenkins_shared_library.pipelines.generic.models.*
import org.zowe.jenkins_shared_library.scm.GitHub

/**
 * Extends the functionality available in the {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline} class. This class adds methods for
 * building and testing your application.
 *
 * A typical pipeline should include these stage in sequence:
 *
 * - build       : build and generate artifact locally.
 * - test        : test the artifact.
 * - publish     : publish the artifact to Artifactory. If the build is a release, we publish the artifact to release folder.
 * - release     : if the build is a release, we tag the GitHub. If the build is a formal release, we also bump release on code base.
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}</li>
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
 *     // Define some protected branches
 *     pipeline.protectedBranches.addMap([
 *         [name: "master"],
 *         [name: "beta"],
 *         [name: "rc"]
 *     ])
 *
 *     // Define the git configuration
 *     pipeline.configureGitHub ([
 *         email: 'git-user-email@example.com',
 *         credentialsId: 'git-user-credentials-id'
 *     ])
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup()
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
     * Text used for the CI SKIP commit.
     */
    protected static final String _CI_SKIP = "[ci skip]"

    /**
     * Temporary upload spec name
     */
    protected static final String temporaryUploadSpecName = '.tmp-pipeline-publish-spec.json'

    /**
     * Stores the change information for reference later.
     */
    final ChangeInformation changeInfo

    /**
     * Branches where we can do a formal release like v2.3.4.
     *
     * Default formal release branches are:
     * - master:         regular release branch
     * - v?.x/master:    LTS v?.x release branch
     */
    List<String> formalReleaseBranches = [
        'master',
        'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/master',
    ]

    /**
     * Branches where we can do a release like v2.3.4 and v2.3.4-beta.1.
     *
     * Default release branches are:
     * - master:         regular release branch
     * - v?.x/master:    LTS v?.x release branch
     * - staging:        regular release branch
     * - v?.x/staging:   LTS v?.x release branch
     */
    List<String> releaseBranches = [
        'master',
        'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/master',
        'staging',
        'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/staging',
    ]

    /**
     * Branch tags
     *
     * This tag will be used to define the way we publish & release
     */
    Map<String, String> branchTags = [
        'master': 'snapshot',
        '(v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?)/master': '$1-snapshot',
    ]

    /**
     * Default branch tag
     */
    String defaultBranchTag = "snapshot"

    /**
     * Default artifactory upload path
     *
     * Allowed macros:
     *
     * - package: value defined by pipeline.setPackage(name)
     * - subproject: optional value passed when parsing the path string
     * - version: the current version
     * - branchtag: branch tag
     */
    String artifactoryUploadTargetPath = '{repository}/{package}{subproject}/{version}{branchtag}/'

    /**
     * GitHub instance
     */
    GitHub github

    /**
     * JFrogArtifactory instance
     */
    JFrogArtifactory artifactory

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
    }

    /**
     * Initialize github configurations
     *
     * @param config            github configuration object
     */
    void configureGitHub(GitConfig config) {
        github.init([
            'email'                      : config.email,
            'usernamePasswordCredential' : config.credentialsId,
        ])
    }

    /**
     * Initialize github configurations
     *
     * @param config            github configuration object
     */
    void configureGitHub(Map config) {
        this.configureGitHub(config as GitConfig)
    }

    /**
     * Initialize artifactory configurations
     *
     * @param config            artifactory configuration object
     */
    void configureArtifactory(ArtifactoryConfig config) {
        artifactory.init([
            'url'                        : config.url,
            'usernamePasswordCredential' : config.credentialsId,
        ])
    }

    /**
     * Initialize artifactory configurations
     *
     * @param config            artifactory configuration object
     */
    void configureArtifactory(Map config) {
        this.configureArtifactory(config as ArtifactoryConfig)
    }

    /**
     * Add branches which can do release
     * @param branches      branch list
     */
    void addReleaseBranches(String... branches) {
        for (String branch : branches) {
            releaseBranches << branch
        }
    }

    /**
     * If current pipeline branch can do a release
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

        for (String releaseBranch : releaseBranches) {
            if (branch.matches(releaseBranch)) {
                result = true
                break
            }
        }

        result
    }

    /**
     * If current build is a release build
     * @return            true or false
     */
    protected Boolean isPerformingRelease() {
        if (steps && steps.params && steps.params['Perform Release']) {
            return true
        } else {
            return false
        }
    }

    /**
     * Get branch tag
     * @param  branch     the branch name to check. By default, empty string will check current branch
     * @return            tag of the branch
     */
    protected String getBranchTag(String branch = '') {
        // use BRANCH_NAME as default value
        if (!branch && steps.env && steps.env.BRANCH_NAME) {
            branch = steps.env.BRANCH_NAME
        }

        String result = defaultBranchTag

        if (branch) {
            for (String branchTag : branchTags) {
                def replaced = branch.replaceAll(branchTag.key, branchTag.value)
                if (branch != replaced) { // really replaced
                    result = replaced
                    break
                }
            }
        }

        result
    }

    protected Map fillArtifactoryUploadTargetPathDefaultMacros(Map macros = [:]) {
        if (!macros.containsKey('repository')) {
            macros['repository'] = this.isReleaseBranch() && this.isPerformingRelease() ?
                                   JFrogArtifactory.REPOSITORY_RELEASE :
                                   JFrogArtifactory.REPOSITORY_SNAPSHOT
        }

        if (!macros.containsKey('package')) {
            macros['package'] = this.packageName
        }
        if (!macros['package']) {
            throw new PublishStageException('Cannot determin package name for artifact upload path', '-')
        }
        if (!macros.containsKey('subproject')) {
            macros['subproject'] = ''
        } else if (!macros['subproject'].startsWith('/')) {
            macros['subproject'] = '/' + macros['subproject']
        }
        if (!macros.containsKey('version')) {
            macros['version'] = this.getVersion()
        }
        if (!macros['version']) {
            throw new PublishStageException('Cannot determin version for artifact upload path', '-')
        }
        if (!macros.containsKey('branchtag')) {
            macros['branchtag'] = this.getBranchTag()
        }

        return macros
    }

    /**
     * Parse Artifactory upload target path
     * @param  macros     map of macros to replace
     * @return           return target path
     */
    protected String parseArtifactoryUploadTargetPath(Map macros = [:]) {
        String target = artifactoryUploadTargetPath

        // provide default values for known macros
        macros = this.fillArtifactoryUploadTargetPathDefaultMacros(macros)

        log.fine("parseArtifactoryUploadTargetPath macros: ${macros}")

        for (String m : macros) {
            target = target.replace(m.key, m.value)
        }

        return target
    }

    /**
     * Calls {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline#setupBase()} to setup the build.
     *
     * @Stages
     * This method adds 2 stages to the build:
     *
     * <dl>
     *     <dt><b>Check for CI Skip</b></dt>
     *     <dd>
     *         Checks that the build commit doesn't contain the CI Skip indicator. If the pipeline finds
     *         the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     *         will also be skipped. The build will also be marked as not built in this scenario.
     *     </dd>
     * </dl>
     *
     * @Note This method was intended to be called {@code setup} but had to be named
     * {@code setupGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     */
    void setupGeneric(GenericSetupArguments timeouts) {
        // Call setup from the super class
        super.setupBase(timeouts)

        createStage(name: 'Check for CI Skip', stage: {
            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${_CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(ResultEnum.NOT_BUILT)
            }
        }, timeout: timeouts.ciSkip)
    }

    /**
     * Initialize the pipeline.
     *
     * @param timeouts A map that can be instantiated as {@link GenericSetupArguments}
     * @see #setupGeneric(GenericSetupArguments)
     */
    void setupGeneric(Map timeouts = [:]) {
        setupGeneric(timeouts as GenericSetupArguments)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param timeouts A map that can be instantiated as {@link SetupArguments}
     */
    @Override
    protected void setup(Map timeouts = [:]) {
        setupGeneric(timeouts)
    }

    /**
     * Signal that no more stages will be added and begin pipeline execution.
     *
     * @param options Options to send to {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline#endBase(java.util.Map)}
     */
    void endGeneric(Map options = [:]) {
        // can we do a release? if so, allow a release parameter
        if (isReleaseBranch()) {
            this.addBuildParameter(steps.booleanParam(
                name         : 'Perform Release',
                description  : "Perform a release of the project. A release will lead to a GitHub tag be created. After a formal release (which doesn't have pre-release string), your branch release will be bumped a PATCH level up. By default, release can only be enabled on ${releaseBranches.join(', ')} branches.",
                defaultValue : false
            ))
            this.addBuildParameter(steps.string(
                name         : 'Pre-Release String',
                description  : "Pre-release string for a release. For example: rc.1, beta.1, etc. This is required if the release is not performed on \"Formal Release Branches\" like ${formalReleaseBranches.join(', ')}",
                defaultValue : '',
                trim         : true
            ))
        }

        super.endBase(options)
    }

    /**
     * Pseudo end method, should be overridden by inherited classes
     * @param args A map that can be instantiated as {@link EndArguments}.
     */
    @Override
    protected void end(Map args = [:]) {
        endGeneric(args)
    }

    /**
     * Creates a stage that will build a generic package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link BuildStageArguments} class. The
     * {@link BuildStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Build: {@link BuildStageArguments#name}</b></dt>
     *     <dd>
     *         Runs the build of your application. The build stage also ignores any
     *         {@link BuildStageArguments#resultThreshold} provided and only runs
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
     *         <dt><b>{@link BuildStageException}</b></dt>
     *         <dd>When arguments.stage is provided. This is an invalid argument field for the operation.</dd>
     *         <dd>When called more than once in your pipeline. Only one build may be present in a
     *             pipeline.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When arguments.operation is not provided.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code build} but had to be named
     * {@code buildGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link BuildStageArguments} used to define
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
     * @param arguments A map of arguments to be applied to the {@link BuildStageArguments} used to define
     *                  the stage.
     */
    protected void build(Map arguments = [:]) {
        buildGeneric(arguments)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} class.</p>
     *
     * @Stages
     * This method adds the following stage to the build:
     *
     * <dl>
     *     <dt><b>Test: {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#name}</b></dt>
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
     *             <dt><b>Test Results HTML Report (REQUIRED)</b></dt>
     *             <dd>
     *                 This is an html report that contains the result of the build. The report must be defined to
     *                 the method in the {@link TestStageArguments#testResults} variable.
     *             </dd>
     *             <dt><b>Code Coverage HTML Report</b></dt>
     *             <dd>
     *                 This is an HTML report generated from code coverage output from your build. The report can
     *                 be omitted by omitting {@link TestStageArguments#coverageResults}
     *             </dd>
     *             <dt><b>JUnit Report (REQUIRED)</b></dt>
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
     *             Code Coverage HTML Report and the Cobertura Report are then captured. The build will fail if
     *             these reports are to be collected and were missing.
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
     *         <dd>When {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#testResults} is missing</dd>
     *         <dd>When invalid options are specified for {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#testResults}</dd>
     *         <dd>When {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#coverageResults} is provided but has an invalid format</dd>
     *         <dd>When {@link TestStageArguments#junit} is missing.</dd>
     *         <dd>When {@link TestStageArguments#operation} is missing.</dd>
     *         <dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code test} but had to be named
     * {@code testGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
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
            steps.junit args.junit

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
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    protected void test(Map arguments = [:]) {
        testGeneric(arguments)
    }

    /**
     * Creates a stage that will publish artifacts to Artifactory.
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
     * {@code publishGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments The arguments for the publish step. {@code arguments.operation} must be
     *                        provided.
     */
    void publishGeneric(Map arguments) {
        arguments.resultThreshold = ResultEnum.SUCCESS

        PublishStageArguments args = arguments as PublishStageArguments

        args.name = "Publish${arguments.name ? ": ${arguments.name}" : ""}"

        PublishStageException preSetupException

        if (args.stage) {
            preSetupException = new PublishStageException("arguments.stage is an invalid option for publishGeneric", args.name)
        }

        // Execute the stage if this is a protected branch and the original should execute function
        // are both true
        args.shouldExecute = {
            boolean shouldExecute = true

            if (arguments.shouldExecute) {
                shouldExecute = arguments.shouldExecute()
            }

            return shouldExecute && _isProtectedBranch
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

            // execute operation Closure if provided
            if (args.operation) {
                args.operation(stageName)
            }

            // upload artifacts if provided
            if (args.artifacts && args.artifacts.size() > 0) {
                def targetPath = args.publishTargetPath ? args.publishTargetPath : parseArtifactoryUploadTargetPath()
                this.uploadArtifacts(args.artifacts, targetPath)
            } else {
                steps.echo "No artifacts to publish."
            }
        }

        createStage(args)
    }

    protected void uploadArtifacts(List<String> artifacts, String targetPath) {
        Map uploadSpec = ["files": []]
        artifacts.each {
            uploadSpec['files'].push([
                "pattern" : it,
                "target"  : targetPath
            ])
        }

        log.fine("Spec of uploading artifact: ${uploadSpec}")

        steps.writeJSON file: temporaryUploadSpecName, json: uploadSpec
        artifactory.upload(spec: temporaryUploadSpecName)
    }

    /**
     * Pseudo publish method, should be overridden by inherited classes
     * @param arguments The arguments for the publish step. {@code arguments.operation} must be
     *                        provided.
     */
    protected void publish(Map arguments) {
        publishGeneric(arguments)
    }

    /**
     * Creates a stage that will execute a release
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
     *     <dd>This stage is responsible for bumping the release of your application source.</dd>
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
     *         <dd>When a test stage has not executed.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code release} but had to be named
     * {@code releaseGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link ReleaseStageArguments} used to define the stage.
     */
    void releaseGeneric(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        GenericStageArguments args = arguments as GenericStageArguments

        ReleaseStageException preSetupException

        if (args.stage) {
            preSetupException = new ReleaseStageException("arguments.stage is an invalid option for releaseGeneric", args.name)
        }

        args.name = "Releasing${arguments.name ? ": ${arguments.name}" : ""}"

        // Execute the stage if this is a protected branch and the original should execute function are both true
        args.shouldExecute = {
            boolean shouldExecute = true

            if (arguments.shouldExecute) {
                shouldExecute = arguments.shouldExecute()
            }

            return shouldExecute && _isProtectedBranch
        }

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (_control.build?.status != StageStatus.SUCCESS) {
                throw new ReleaseStageException("Build must be successful to publish", args.name)
            } else if (_control.prePublishTests && _control.prePublishTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new ReleaseStageException("All test stages before publish must be successful or skipped!", args.name)
            } else if (_control.prePublishTests.size() == 0) {
                throw new ReleaseStageException("At least one test stage must be defined", args.name)
            }

            args.operation(stageName)
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage release = createStage(args)
        if (!_control.release) {
            _control.release = release
        }
    }

    /**
     * Pseudo release method, should be overridden by inherited classes
     * @param arguments The arguments for the release step. {@code arguments.operation} must be
     *                        provided.
     */
    protected void release(Map arguments) {
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
