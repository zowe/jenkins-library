/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.pipelines.gradle

import groovy.util.logging.Log
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.runtime.InvokerHelper
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.jenkins_shared_library.gradle.Gradle
import org.zowe.jenkins_shared_library.pipelines.base.Branches
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage
import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.Build
import org.zowe.jenkins_shared_library.pipelines.Constants
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.ReleaseStageArguments
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline
import org.zowe.jenkins_shared_library.pipelines.gradle.arguments.*
import org.zowe.jenkins_shared_library.pipelines.gradle.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.gradle.models.*
import org.zowe.jenkins_shared_library.scm.ScmException
import org.zowe.jenkins_shared_library.Utils

/**
 * Extends the functionality available in the {@link jenkins_shared_library.pipelines.generic.GenericPipeline} class.
 * This class adds more advanced functionality to build, test, and deploy your Gradle application.
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link jenkins_shared_library.pipelines.generic.GenericPipeline}</li>
 *     <li><a href="https://plugins.jenkins.io/pipeline-utility-steps">Pipeline Utility Steps</a></li>
 *     <li><a href="https://plugins.jenkins.io/pipeline-input-step">Pipeline: Input Step</a></li>
 * </ul>
 * </dd></dl>
 *
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.jenkins_shared_library.pipelines.gradle.GradlePipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     GradlePipeline pipeline = new GradlePipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.admins.add("userid1", "userid2", "userid3")
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup(
 *         // Define the git configuration
 *         github: [
 *             email: 'robot-user@example.com',
 *             usernamePasswordCredential: 'robot-user'
 *         ],
 *         // Define the artifactory configuration
 *         artifactory: [
 *             url : 'https://your-artifactory-url',
 *             usernamePasswordCredential : 'artifactory-credential-id',
 *         ],
 *     )
 *
 *     // Create custom stages for your build like this
 *     pipeline.createStage(name: 'Some Stage", stage: {
 *         echo "This is my stage"
 *     })
 *
 *     // Run a build
 *     pipeline.build()   // Provide required parameters in your pipeline
 *
 *     // Run a test
 *     pipeline.test()    // Provide required parameters in your pipeline
 *
 *     // Run a SonarQube code scan
 *     pipeline.sonarScan()
 *
 *     // Create package of your build result
 *     pipeline.packaging()    // Provide required parameters in your pipeline
 *
 *     // publish artifact to artifactory
 *     pipeline.publish() // Provide required parameters in your pipeline
 *
 *     // release version bump and git tag
 *     pipeline.release() // Provide required parameters in your pipeline
 *
 *     // MUST BE CALLED LAST
 *     pipeline.end()
 * }
 * </pre>
 *
 * <p>In the example above, the stages will run on a node labeled {@code 'pipeline-node'}. You must
 * define the node where your pipeline will execute. This node must have the ability to execute an
 * <a href="https://en.wikipedia.org/wiki/Expect">Expect Script</a>.</p>
 */
@Log
class GradlePipeline extends GenericPipeline {
    /**
     * Default gradle.properties file name.
     *
     * @Default {@code "gradle.properties"}
     */
    public static final String GRADLE_PROPERTIES = 'gradle.properties'

    /**
     * A map of branches.
     */
    Branches<GradleBranch> branches = new Branches<>(GradleBranch.class)

    /**
     * Gradle instance
     */
    Gradle gradle

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the GradlePipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pipeline = new GradlePipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    GradlePipeline(steps) {
        super(steps)

        this.gradle = new Gradle(steps)
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
     * Calls {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#setupGeneric()} to setup the build.
     *
     * <p>This method adds extra initialization steps to the default setup "Init Generic Pipeline"
     * stage. The initialization will try to extract package information, like name, version, etc
     * from gradle properties.</p>
     */
    void setupGradle(GradleSetupStageArguments arguments) throws GradlePipelineException {
        Closure initGradle = { pipeline ->
            // init gradle settings
            pipeline.steps.echo 'Init Gradle project ...'
            pipeline.gradle.init()

            // extract project information from gradle
            pipeline.packageInfo = pipeline.gradle.getPackageInfo()
            if (!pipeline.packageInfo['versionTrunks'] ||
                pipeline.packageInfo['versionTrunks']['prerelease'] ||
                pipeline.packageInfo['versionTrunks']['metadata']) {
                throw new GradlePipelineException('Version defined in gradle properties shouldn\'t have pre-release string or metadata, pipeline will adjust based on branch and build parameter.')
            }
            if (pipeline.packageInfo['group']) {
                pipeline.setPackageName(pipeline.packageInfo['group'])
            }

            // version could be used to publish artifact
            pipeline.setVersion(pipeline.packageInfo['version'])
            pipeline.steps.echo "Package information: ${pipeline.getPackageName()} v${pipeline.getVersion()}"

            // gradle needs special version format if we use gradle to publish artifact
            Map<String, String> macros = pipeline.getBuildStringMacros()
            String gradleVersion = "${macros['version']}${macros['branchtag-uc']}".toString()
            if (macros['branchtag-uc'] != '') {
                if (!gradleVersion.endsWith('-SNAPSHOT')) {
                    // non formal release must end with -SNAPSHOT
                    gradleVersion = "${gradleVersion}-SNAPSHOT".toString()
                }
                pipeline.gradle._updateVersion(gradleVersion)
            }
            pipeline.steps.echo "Maven publish plugin target: ${gradleVersion}"
        }
        // should we overwrite this?
        arguments.extraInit = initGradle
        super.setupGeneric(arguments)

        // prepare default configurations
        this.defineDefaultBranches()
    }

    /**
     * Initialize the pipeline.
     *
     * @param arguments A map that can be instantiated as {@link GradleSetupStageArguments}
     * @see #setup(GradleSetupStageArguments)
     */
    void setupGradle(Map arguments = [:]) {
        // if the Arguments class is not base class, the {@code "arguments as SomeStageArguments"} statement
        // has problem to set values of properties defined in super class.
        GradleSetupStageArguments args = new GradleSetupStageArguments()
        InvokerHelper.setProperties(args, arguments)
        setupGradle(args)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param arguments A map that can be instantiated as {@link GradleSetupStageArguments}
     */
    @Override
    protected void setup(Map arguments = [:]) {
        setupGradle(arguments)
    }

    /**
     * Creates a stage that will build jars of your project.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)}
     * method and will have the following additional operations. <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments#operation} is not
     *     provided, the stage will default to executing {@code ./gradlew assemble}.</li>
     * </ul></p>
     *
     * <p>The build is performed by gradle {@code assemble} task. Based on Gradle Java plugin
     * {@link https://docs.gradle.org/current/userguide/java_plugin.html}, the diagram shows the
     * relationships between these tasks. Assemble is a task to create a build, but won't run test
     * task.
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void buildGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = { String stageName ->
                // assemble doesn't include running test
                steps.sh "./gradlew assemble"
            }
        }

        super.buildGeneric(arguments)
    }

    /**
     * Pseudo build method, should be overridden by inherited classes
     * @param arguments A map of arguments to be applied to the {@link BuildStageArguments} used to define
     *                  the stage.
     */
    @Override
    protected void build(Map arguments = [:]) {
        buildGradle(arguments)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments#operation} is not
     *     provided, this method will default to executing {@code ./gradlew coverage} or {@code ./gradlew check}.</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    void testGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = { String stageName ->
                // check is the next further step than test
                if (this.packageInfo && this.packageInfo['scripts'] && this.packageInfo['scripts'].contains('coverage')) {
                    steps.echo 'gradle coverage task is defined.'
                    steps.sh "./gradlew coverage"
                } else {
                    steps.echo 'gradle coverage task is not defined, will run check instead.'
                    steps.sh "./gradlew check"
                }
            }
        }

        super.testGeneric(arguments)
    }

    /**
     * Pseudo test method, should be overridden by inherited classes
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    @Override
    protected void test(Map arguments = [:]) {
        testGradle(arguments)
    }

    /**
     * Creates a stage that will execute SonarQube code scan on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.gradle.arguments.GradleSonarScanStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#sonarScanGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.gradle.arguments.GradleSonarScanStageArguments#operation} is not
     *     provided, this method will default to executing {@code ./gradlew sonarqube}. You can disable this behavior by passing
     *     {@link org.zowe.jenkins_shared_library.pipelines.gradle.arguments.GradleSonarScanStageArguments#disableSonarGradlePlugin} as {@code true}.</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.gradle.arguments.GradleSonarScanStageArguments} used to define
     *                  the stage.
     */
    void sonarScanGradle(GradleSonarScanStageArguments arguments) throws SonarScanStageException {
        if (!arguments.operation && !arguments.disableSonarGradlePlugin) {
            arguments.operation = { String stageName ->
                if (!arguments.scannerServer) {
                    throw new SonarScanStageException("arguments.scannerServer is not defined for sonarScanGeneric", arguments.name)
                }
                steps.withSonarQubeEnv(arguments.scannerServer) {
                    def scannerParam = steps.readJSON text: steps.env.SONARQUBE_SCANNER_PARAMS
                    if (!scannerParam || !scannerParam['sonar.host.url']) {
                        error "Unable to find sonar host url from SONARQUBE_SCANNER_PARAMS: ${scannerParam}"
                    }
                    if (!scannerParam || !scannerParam['sonar.login']) {
                        error "Unable to find sonar authentication from SONARQUBE_SCANNER_PARAMS: ${scannerParam}"
                    }

                    def gradleParams = " -Psonar.host.url=${scannerParam['sonar.host.url']}" +
                                       " -Psonar.login=${scannerParam['sonar.login']}" +
                                       " -Psonar.links.ci=${steps.env.BUILD_URL}" +
                                       " -x test -x compileJava -x compileTestJava"

                    if (arguments.allowBranchScan) {
                        // pass branch information
                        if (this.changeInfo.isPullRequest) {
                            gradleParams = gradleParams + " -Psonar.pullrequest.key=${this.changeInfo.pullRequestId}"
                            gradleParams = gradleParams + " -Psonar.pullrequest.branch=${this.changeInfo.changeBranch}"
                            gradleParams = gradleParams + " -Psonar.pullrequest.base=${this.changeInfo.baseBranch}"
                        } else {
                            gradleParams = gradleParams + " -Psonar.branch.name=${this.changeInfo.branchName}"
                        }
                    }

                    def extraEnvironments = ""
                    if (arguments.javaHome) {
                        // warning The version of Java (1.8.0_242) you have used to run this analysis is deprecated and we will stop accepting it from October 2020. Please update to at least Java 11.
                        extraEnvironments = "JAVA_HOME=${arguments.javaHome}\nPATH=\${JAVA_HOME}/bin:\$PATH\n"
                    }
                    if (arguments.nodeJsVersion && arguments.nvmInitScript) {
                        // The version of node.js (8) you have used to run this analysis is deprecated and we stopped accepting it. Please update to at least node.js 10.
                        // Temporarily you can set the property 'sonar.scanner.force-deprecated-node-version-grace-period' to 'true' to continue using node.js 8
                        // This will only work until Mon Feb 15 08:00:00 UTC 2021, afterwards all scans will fail.
                        extraEnvironments += "set +x\n. ${arguments.nvmInitScript}\nnvm install ${arguments.nodeJsVersion}\nnvm use ${arguments.nodeJsVersion}\nset -x\n"
                    }

                    // Per Sonar Doc - It's important to add --info because of SONARJNKNS-281
                    steps.sh "${extraEnvironments} ./gradlew --info sonarqube ${gradleParams}"

                    // check build status
                    if (arguments.failBuild) {
                        // get task id
                        String sonarTaskId = this.steps.sh(
                            script: 'cat build/sonar/report-task.txt | grep \'ceTaskId=\' | awk -F= \'{print $2;}\'',
                            returnStdout: true
                        ).trim()
                        if (!sonarTaskId) {
                            steps.echo "Files in build folder:"
                            steps.sh 'find build'
                            steps.error 'Failed to find Sonar scan task ID.'
                        }
                        steps.echo "Sonar scan task ID is ${sonarTaskId}."
                        String sonarTaskUrl = "${scannerParam['sonar.host.url']}/api/ce/task?id=${sonarTaskId}".toString()

                        // check task status
                        String sonarTaskStatus = "PENDING"
                        while (sonarTaskStatus == "PENDING" || sonarTaskStatus == "IN_PROGRESS") {
                            steps.echo "[QualityGate] Requesting task status from URL: ${sonarTaskUrl}"
                            sonarTaskStatus = this.steps.sh(
                                script: "curl -s -u '${scannerParam['sonar.login']}:' '${sonarTaskUrl}' | jq -r '.task.status'",
                                returnStdout: true
                            ).trim()
                            steps.echo "[QualityGate] Current status is ${sonarTaskStatus}."
                            steps.sleep(1)
                        }

                        if (sonarTaskStatus == "FAILED" || sonarTaskStatus == "CANCELED") {
                            steps.error "[QualityGate] Task failed or was canceled."
                        } else if (sonarTaskStatus == "SUCCESS") {
                            String analysisId = this.steps.sh(
                                script: "curl -s -u '${scannerParam['sonar.login']}:' '${sonarTaskUrl}' | jq -r '.task.analysisId'",
                                returnStdout: true
                            ).trim()
                            steps.echo "[QualityGate] Task analysis id is ${analysisId}."
                            // once the task is finished on the server we can check the result
                            String analysisUrl = "${scannerParam['sonar.host.url']}/api/qualitygates/project_status?analysisId=${analysisId}".toString()
                            steps.echo "[QualityGate] Task finished, checking the result at ${analysisUrl}"
                            String sonarProjectStatus = this.steps.sh(
                                script: "curl -s -u '${scannerParam['sonar.login']}:' '${analysisUrl}' | jq -r '.projectStatus.status'",
                                returnStdout: true
                            ).trim()
                            steps.echo "[QualityGate] Project analysis result is ${sonarProjectStatus}."
                            if (sonarProjectStatus == "OK") {
                                steps.echo "[QualityGate] Analysis passed the quality gate."
                            } else if (sonarProjectStatus == "ERROR") {
                                steps.error "[QualityGate] Analysis did not pass the quality gate."
                            } else {
                                steps.error "[QualityGate] Unknown quality gate status: '${sonarProjectStatus}'"
                            }
                        } else {
                            steps.error "[QualityGate] Unknown task status ${sonarTaskStatus}. Aborting."
                        }
                    }
                }
            }
        }

        super.sonarScanGeneric(arguments)
    }

    /**
     * Creates a stage that will execute SonarQube code scan on your application.
     *
     * @param arguments A map that can be instantiated as {@link GradleSonarScanStageArguments}
     * @see #sonarScanGradle(GradleSonarScanStageArguments)
     */
    void sonarScanGradle(Map arguments = [:]) {
        GradleSonarScanStageArguments args = new GradleSonarScanStageArguments()
        InvokerHelper.setProperties(args, arguments)
        sonarScanGradle(args)
    }

    /**
     * Pseudo sonarScan method, should be overridden by inherited classes
     * @param arguments The arguments for the sonarScan step.
     */
    @Override
    protected void sonarScan(Map arguments = [:]) {
        sonarScanGradle(arguments)
    }

    /**
     * Creates a stage that will execute SonarQube code scan on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#packagingGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments#operation} is not
     *     provided, this method will default to executing {@code ./gradlew jar}.</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PackagingStageArguments} used to define
     *                  the stage.
     */
    void packagingGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = { String stageName ->
                // jar task actually has been executed becuase it's pre-task for assemble
                steps.sh "./gradlew jar"
            }
        }

        super.packagingGeneric(arguments)
    }

    /**
     * Pseudo packaging method, should be overridden by inherited classes
     * @param arguments The arguments for the packaging step.
     */
    @Override
    protected void packaging(Map arguments) {
        packagingGradle(arguments)
    }

    /**
     * This method overrides and perform version bump on gradle project.
     *
     * <p>By default, the {@code version} defined in {@code gradle.properties} will be bumped.</p>
     */
    @Override
    protected void bumpVersion() {
        def branch = this.github.branch
        if (!branch) {
            // try to detect branch name
            this.github.initFromFolder()
            branch = this.github.branch
        }
        if (!branch) {
            throw new GradlePipelineException('Unable to determine branch name to for version bump.')
        }
        this.gradle.version(this.github, branch, 'patch')
    }
}
