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
import java.util.regex.Pattern
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
 *     pipeline.gitConfig = [
 *         email: 'git-user-email@example.com',
 *         credentialsId: 'git-user-credentials-id'
 *     ]
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup()
 *
 *     pipeline.buildGeneric()  //////////////////////////////////////////////////
 *     pipeline.testGeneric()   // Provide required parameters in your pipeline //
 *     pipeline.deployGeneric() //////////////////////////////////////////////////
 *
 *     // MUST BE CALLED LAST
 *     pipeline.end()
 * }
 * </pre>
 */
class GenericPipeline extends Pipeline {
    /**
     * Text used for the CI SKIP commit.
     */
    protected static final String _CI_SKIP = "[ci skip]"

    /**
     * If we want to check [ci skip] exists in last commit and decide to skip build
     */
    Boolean allowCiSkip = true

    /**
     * Stores the change information for reference later.
     */
    final ChangeInformation changeInfo

    /**
     * Branches where we can do a release
     */
    List<String> releaseBranches = ['master', 'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/master']

    /**
     * Options when we do a release
     */
    List<String> releaseOptions = ['SNAPSHOT', 'PATCH', 'MINOR', 'MAJOR']

    /**
     * GitHub instance
     */
    GitHub github

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
    }

    /**
     * Initialize github configurations
     *
     * @param gitConfig            github configuration object
     */
    void configureGitHub(GitConfig gitConfig) {
        // current git origin and use as configuration

        github.init([
            'email'                      : gitConfig.email,
            'usernamePasswordCredential' : gitConfig.credentialsId,
        ])
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
     * @return               true or false
     */
    protected Boolean isReleaseBranch() {
        // not a multibranch pipeline, always allow release?
        if (!steps.env || !steps.env.BRANCH_NAME) {
            return true
        }

        def result = false

        for (String releaseBranch : releaseBranches) {
            if (steps.env.BRANCH_NAME.matches(releaseBranch)) {
                result = true
                break
            }
        }

        result
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

        if (allowCiSkip) {
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
            buildParameters.push(choice(
                name: 'PERFORM_RELEASE',
                description: 'Publish a release or snapshot version. By default, this task will create snapshot. If you choose release other than snapshot, your branch version will bump up. Release can only be enabled on `master`, LTS version branch like `v1.x/master`, or branches which enabled to release.',
                choices: ['SNAPSHOT', 'PATCH', 'MINOR', 'MAJOR']
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
     *                 {@link TestStageArguments#junitOutput}
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
     *         <dd>When {@link TestStageArguments#junitOutput} is missing.</dd>
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

            if (!args.testResults) {
                throw new TestStageException("Test Results HTML Report not provided", args.name)
            } else {
                _validateReportInfo(args.testResults, "Test Results HTML Report", args.name)
            }

            if (!args.coverageResults) {
                steps.echo "Code Coverage HTML Report not provided...report ignored"
            } else {
                _validateReportInfo(args.coverageResults, "Code Coverage HTML Report", args.name)
            }

            if (!args.junitOutput) {
                throw new TestStageException("JUnit Report not provided", args.name)
            }

            if (!args.operation) {
                throw new DeployStageException("Missing test operation!", args.name)
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
            steps.junit args.junitOutput

            // Collect Test Results HTML Report
            steps.publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : args.testResults.dir,
                    reportFiles          : args.testResults.files,
                    reportName           : args.testResults.name
            ])

            // Collect coverage if applicable
            if (args.coverageResults) {
                steps.publishHTML(target: [
                        allowMissing         : false,
                        alwaysLinkToLastBuild: true,
                        keepAll              : true,
                        reportDir            : args.coverageResults.dir,
                        reportFiles          : args.coverageResults.files,
                        reportName           : args.coverageResults.name
                ])
            }

            // Collect cobertura coverage if specified
            if (args.cobertura) {
                steps.cobertura(TestStageArguments.coberturaDefaults + args.cobertura)
            } else if (args.coverageResults) {
                steps.echo "WARNING: Cobertura file not detected, skipping"
            }
        }

        // Create the stage and ensure that the tests are properly added.
        Stage test = createStage(args)
        if (!(_control.version || _control.deploy)) {
            _control.preDeployTests += test
        }
    }

    /**
     * Creates a stage that will execute a version bump and then deploy. test
     *
     * @Stages
     * This method can add 2 stages to the build:
     *
     * <dl>
     *     <dt><b>Versioning</b></dt>
     *     <dd>This stage is responsible for bumping the version of your application source. It will only
     *         be added if <b>versionArguments</b> is a non-empty map.</dd>
     *     <dt><b>Deploy</b></dt>
     *     <dd>This stage is responsible for deploying your application source. It will always execute
     *         after Versioning (if present).</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     Both stages will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is
     *         {@link ResultEnum#SUCCESS} or higher.</li>
     *         <li>The stage will only execute if the current branch is protected.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     Both the Version stage and the Deploy stage will throw the following exceptions:
     *
     *     <dl>
     *         <dt><b>{@link DeployStageException}</b></dt>
     *         <dd>When stage is provided as an argument. This is an invalid parameter for both
     *             stages</dd>
     *         <dd>When a test stage has not executed. This prevents untested code from being
     *             deployed</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code deploy} but had to be named
     * {@code deployGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param deployArguments The arguments for the deploy step. {@code deployArguments.operation} must be
     *                        provided.
     * @param versionArguments The arguments for the versioning step. If provided, then
     *                         {@code versionArguments.operation} must be provided.
     */
    void deployGeneric(Map deployArguments, Map versionArguments = [:]) {
        if (versionArguments.size() > 0) {
            versionGeneric(versionArguments)
        }

        deployArguments.resultThreshold = ResultEnum.SUCCESS

        GenericStageArguments args = deployArguments as GenericStageArguments

        args.name = "Deploy${deployArguments.name ? ": ${deployArguments.name}" : ""}"

        DeployStageException preSetupException

        if (args.stage) {
            preSetupException = new DeployStageException("arguments.stage is an invalid option for deployGeneric", args.name)
        }

        // Execute the stage if this is a protected branch and the original should execute function
        // are both true
        args.shouldExecute = {
            boolean shouldExecute = true

            if (deployArguments.shouldExecute) {
                shouldExecute = deployArguments.shouldExecute()
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
                throw new DeployStageException("Build must be successful to deploy", args.name)
            } else if (_control.preDeployTests && _control.preDeployTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new DeployStageException("All test stages before deploy must be successful or skipped!", args.name)
            } else if (_control.preDeployTests.size() == 0) {
                throw new DeployStageException("At least one test stage must be defined", args.name)
            }

            args.operation(stageName)
        }

        createStage(args)
    }

    /**
     * Creates a stage that will execute a version bump
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link VersionStageArguments} class. The
     * {@link VersionStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Versioning: {@link VersionStageArguments#name}</b></dt>
     *     <dd>This stage is responsible for bumping the version of your application source.</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     This stage will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is {@link ResultEnum#SUCCESS} or higher.</li>
     *         <li>The stage will only execute if the current branch is protected.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions will be thrown if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link VersionStageException}</b></dt>
     *         <dd>When stage is provided as an argument.</dd>
     *         <dd>When a test stage has not executed.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code version} but had to be named
     * {@code versionGeneric} due to the issues described in {@link org.zowe.jenkins_shared_library.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link VersionStageArguments} used to define the stage.
     */
    void versionGeneric(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        GenericStageArguments args = arguments as GenericStageArguments

        VersionStageException preSetupException

        if (args.stage) {
            preSetupException = new VersionStageException("arguments.stage is an invalid option for deployGeneric", args.name)
        }

        args.name = "Versioning${arguments.name ? ": ${arguments.name}" : ""}"

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
                throw new VersionStageException("Build must be successful to deploy", args.name)
            } else if (_control.preDeployTests && _control.preDeployTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new VersionStageException("All test stages before deploy must be successful or skipped!", args.name)
            } else if (_control.preDeployTests.size() == 0) {
                throw new VersionStageException("At least one test stage must be defined", args.name)
            }

            args.operation(stageName)
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage version = createStage(args)
        if (!_control.version) {
            _control.version = version
        }
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
