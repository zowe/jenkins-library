/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.gradle

import groovy.util.logging.Log
import java.util.concurrent.TimeUnit
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
 * Extends the functionality available in the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline} class.
 * This class adds more advanced functionality to build, test, and deploy your application.
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline}</li>
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
 *     pipeline.branches.addMap([
 *         [name: "master", tag: "daily", prerelease: "alpha"],
 *         [name: "beta", tag: "beta", prerelease: "beta"],
 *         [name: "dummy", tag: "dummy", autoDeploy: true],
 *         [name: "latest", tag: "latest"],
 *         [name: "lts-incremental", tag: "lts-incremental", level: SemverLevel.MINOR],
 *         [name: "lts-stable", tag: "lts-stable", level: SemverLevel.PATCH]
 *     ])
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
 *         // Define install registries
 *         installRegistries: [
 *             [email: 'email@example.com', usernamePasswordCredential: 'credentials-id'],
 *             [registry: 'https://registry.com', email: 'email@example.com', usernamePasswordCredential: 'credentials-id']
 *             [registry: 'https://registry.com', email: 'email@example.com', usernamePasswordCredential: 'credentials-id', scope: '@myOrg']
 *         ],
 *         // Define publish registry
 *         publishRegistry: [
 *             email: 'robot-user@example.com',
 *             usernamePasswordCredential: 'robot-user'
 *         ]
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
    public static final String GRADLE_PROPERTIES = 'gradle.properties'

    /**
     * A map of branches.
     *
     * <p>Any branches that are specified as protected will also have concurrent builds disabled. This
     * is to prevent issues with publishing.</p>
     */
    protected Branches<GradleBranch> branches = new Branches<>(GradleBranch.class)

    /**
     * Package information extracted from package.json
     */
    Map packageInfo

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
                npmTag             : 'latest',
            ],
            [
                name               : 'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/master',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                allowFormalRelease : true,
                releaseTag         : '$1-snapshot',
                npmTag             : '$1-latest',
            ],
            [
                name               : 'staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                npmTag             : 'dev',
            ],
            [
                name               : 'v[0-9]+\\.[0-9x]+(\\.[0-9x]+)?/staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                npmTag             : '$1-dev',
            ],
        ])
    }

    /**
     * Calls {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#setupGeneric()} to setup the build.
     *
     * @Stages
     * This method adds one stage to the build:
     *
     * <dl>
     *     <dt><b>Install Node Package Dependencies</b></dt>
     *     <dd>
     *         <p>
     *             This step will install all your package dependencies via `npm install`. Prior to install
     *             the stage will login to any registries specified in the {@link #registryConfig} array. On
     *             exit, the step will try to logout of the registries specified in {@link #registryConfig}.
     *         </p>
     *         <dl>
     *             <dt><b>Exceptions:</b></dt>
     *             <dd>
     *                 <dl>
     *                     <dt><b>{@link GradlePipelineException}</b></dt>
     *                     <dd>
     *                         When two default registries, a registry that omits a url, are specified.
     *                     </dd>
     *                     <dd>
     *                         When a login to a registry fails. <b>Note:</b> Failure to logout of a
     *                         registry will not result in a failed build.
     *                     </dd>
     *                     <dt><b>{@link Exception}</b></dt>
     *                     <dd>
     *                         When a failure to install dependencies occurs.
     *                     </dd>
     *                 </dl>
     *             </dd>
     *         </dl>
     *     </dd>
     * </dl>
     */
    void setupGradle(GradleSetupArguments arguments) throws GradlePipelineException {
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
            // version could be used to publish artifact
            pipeline.setVersion(pipeline.packageInfo['version'])
            pipeline.steps.echo "Package information: ${pipeline.getPackageName()} v${pipeline.getVersion()}"
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
     * @param arguments A map that can be instantiated as {@link GradleSetupArguments}
     * @see #setup(GradleSetupArguments)
     */
    void setupGradle(Map arguments = [:]) {
        setupGradle(arguments as GradleSetupArguments)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param arguments A map that can be instantiated as {@link SetupArguments}
     */
    @Override
    protected void setup(Map arguments = [:]) {
        setupGradle(arguments)
    }

    /**
     * Creates a stage that will build a GradlePipeline package.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)}
     * method and will have the following additional operations. <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments#operation} is not
     *     provided, the stage will default to executing {@code npm run build}.</li>
     *     <li>After the operation is complete, the stage will use npm pack to generate an
     *     installable artifact. This artifact is archived to the build for later access.</li>
     * </ul></p>
     *
     * Gradle Java plugin: {@link https://docs.gradle.org/current/userguide/java_plugin.html}, diagram shows the relationships between these tasks.
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void buildGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.ansiColor('xterm') {
                    // assemble doesn't include running test
                    steps.sh "./gradlew assemble"
                }
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
     *     provided, this method will default to executing {@code npm run test}</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    void testGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.ansiColor('xterm') {
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

    void packagingGradle(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.ansiColor('xterm') {
                    // jar task actually has been executed becuase it's pre-task for assemble
                    steps.sh "./gradlew jar"
                }
            }
        }

        super.packagingGeneric(arguments)
    }

    /**
     * Pseudo packaging method, should be overridden by inherited classes
     * @param arguments The arguments for the packaging step. {@code arguments.operation} must be
     *                        provided.
     */
    @Override
    protected void packaging(Map arguments) {
        packagingGradle(arguments)
    }

    /**
     * This method should be overridden to properly bump version in different kind of project.
     *
     * For example, npm package should use `npm version patch` to bump, and gradle project should ...
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
