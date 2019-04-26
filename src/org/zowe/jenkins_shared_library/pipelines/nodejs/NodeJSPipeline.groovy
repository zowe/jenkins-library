/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.nodejs

import groovy.util.logging.Log
import java.util.concurrent.TimeUnit
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.jenkins_shared_library.npm.Registry
import org.zowe.jenkins_shared_library.pipelines.base.Branches
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage
import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.Build
import org.zowe.jenkins_shared_library.pipelines.Constants
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.ReleaseStageArguments
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline
import org.zowe.jenkins_shared_library.pipelines.nodejs.arguments.*
import org.zowe.jenkins_shared_library.pipelines.nodejs.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.nodejs.models.*
import org.zowe.jenkins_shared_library.scm.ScmException

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
 * {@literal @}Library('fill this out according to your setup') import org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     NodeJSPipeline pipeline = new NodeJSPipeline(this)
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
class NodeJSPipeline extends GenericPipeline {
    /**
     * This is the id of the approver saved when the pipeline auto approves the deploy.
     */
    static final String AUTO_APPROVE_ID = "[PIPELINE_AUTO_APPROVE]"

    /**
     * This is the id of the approver saved when the pipeline auto approves the deploy because
     * of a timeout.
     */
    static final String TIMEOUT_APPROVE_ID = "[TIMEOUT_APPROVED]"

    /**
     * A map of branches.
     *
     * <p>Any branches that are specified as protected will also have concurrent builds disabled. This
     * is to prevent issues with publishing.</p>
     */
    protected Branches<NodeJSBranch> branches = new Branches<>(NodeJSBranch.class)

    /**
     * Artifactory instance
     */
    Registry publishRegistry

    /**
     * Package information extracted from package.json
     */
    Map packageInfo

    /**
     * Artifactory instances for npm install registries
     */
    List<Registry> installRegistries = []

    /**
     * Default artifactory file name pattern
     */
    String npmPublishTargetVersion = '{version}{branchtag}{buildnumber}{timestamp}'

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the NodeJSPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pipeline = new NodeJSPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    NodeJSPipeline(steps) {
        super(steps)

        publishRegistry = new Registry(steps)
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
     * Try to login to npm publish registry
     */
    void loginToPublishRegistry() {
        // try to determin registry again if not presented
        if (!publishRegistry.registry || !publishRegistry.scope) {
            if (!publishRegistry.registry && this.packageInfo && this.packageInfo.containsKey('registry')) {
                publishRegistry.registry = this.packageInfo['registry']
            }
            if (!publishRegistry.scope && this.packageInfo && this.packageInfo.containsKey('scope')) {
                publishRegistry.scope = this.packageInfo['scope']
            }
        }

        if (publishRegistry.tokenCredential || publishRegistry.usernamePasswordCredential) {
            // this is a custom registry with login credential
            // we need to login
            publishRegistry.login()
        }
    }

    /**
     * Try to login to npm install registries
     */
    void loginToInstallRegistries() {
        for (Registry registry : installRegistries) {
            if (registry.tokenCredential || registry.usernamePasswordCredential) {
                // this is a custom registry with login credential
                // we need to login
                registry.login()
            }
        }
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
     *                     <dt><b>{@link NodeJSPipelineException}</b></dt>
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
    void setupNodeJS(NodeJSSetupArguments arguments) throws NodeJSPipelineException {
        super.setupGeneric(arguments)

        // prepare default configurations
        this.defineDefaultBranches()

        // this stage should always happen for node.js project?
        createStage(name: 'Install Node Package Dependencies', stage: {
            // init registries
            if (arguments.publishRegistry) {
                publishRegistry.init(arguments.publishRegistry)
                if (!publishRegistry.registry) {
                    // try to extract publish registry from package.json
                    publishRegistry.registry = publishRegistry.getRegistryFromPackageJson()
                }
            }
            if (arguments.installRegistries) {
                for (Map config : arguments.installRegistries) {
                    Registry registry = new Registry(steps)
                    registry.init(config)
                    installRegistries.push(registry)
                }
            }

            // try to login to npm install registries
            this.loginToInstallRegistries()
            // init package info from package.json
            this.packageInfo = publishRegistry.getPackageInfo()
            if (!this.packageInfo['versionTrunks'] ||
                this.packageInfo['versionTrunks']['prerelease'] ||
                this.packageInfo['versionTrunks']['metadata']) {
                throw new NodeJSPipelineException('Version defined in package.json shouldn\'t have pre-release string or metadata, pipeline will adjust based on branch and build parameter.')
            }
            // version could be used to publish artifact
            this.setVersion(this.packageInfo['version'])

            if (steps.fileExists('yarn.lock')) {
                steps.sh "yarn install"
            } else {
                // we save audit part to next stage
                if (arguments.alwaysUseNpmInstall) {
                    steps.sh "npm install --no-audit"
                } else {
                    if (steps.fileExists('package-lock.json')) {
                        // if we have package-lock.json, try to use everything defined in that file
                        steps.sh "npm ci"
                    } else {
                        steps.sh "npm install --no-audit"
                    }
                }
            }

            // debug purpose, sometimes npm install will update package-lock.json
            def gitStatus = this.steps.sh(script: 'git status --porcelain', returnStdout: true).trim()
            if (gitStatus != '') {
                this.steps.echo """
======================= WARNING: git folder is not clean =======================
${gitStatus}
============ This may cause fail to publish artifact in later stage ============
"""
                if (arguments.exitIfFolderNotClean) {
                    steps.error 'Git folder is not clean after installing dependencies.'
                } else {
                    // we decide to ignore lock files
                    if (gitStatus == 'M package-lock.json') {
                        this.steps.echo "WARNING: package-lock.json will be reset to ignore the failure"
                        steps.sh 'git checkout -- package-lock.json'
                    } else if (gitStatus == 'M yarn.lock') {
                        this.steps.echo "WARNING: yarn.lock will be reset to ignore the failure"
                        steps.sh 'git checkout -- yarn.lock'
                    } else {
                        steps.error 'Git folder is not clean other than lock files after installing dependencies.'
                    }
                }
            }
        }, isSkippable: false, timeout: arguments.installDependencies)

        // this stage should always happen for node.js project?
        createStage(name: 'Audit', stage: {
            // we should have login to npm install registries
            try {
                steps.sh "npm audit"
            } catch (e) {
                if (arguments.ignoreAuditFailure) {
                    steps.echo "WARNING: npm audit failed with error \"${e}\" but is ignored."
                } else {
                    throw e
                }
            }
        }, isSkippable: false, timeout: arguments.audit)
    }

    /**
     * Initialize the pipeline.
     *
     * @param arguments A map that can be instantiated as {@link NodeJSSetupArguments}
     * @see #setup(NodeJSSetupArguments)
     */
    void setupNodeJS(Map arguments = [:]) {
        setupNodeJS(arguments as NodeJSSetupArguments)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param arguments A map that can be instantiated as {@link SetupArguments}
     */
    @Override
    protected void setup(Map arguments = [:]) {
        setupNodeJS(arguments)
    }

    /**
     * Creates a stage that will build a NodeJSPipeline package.
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
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void buildNodeJS(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.sh "npm run build"
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
        buildNodeJS(arguments)
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
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    void testNodeJS(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = {
                steps.sh "npm run test"
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
        testNodeJS(arguments)
    }

    /**
     * Publish a Node JS package.
     *
     * @Stages
     * This will extend the stages provided by the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#deployGeneric(java.util.Map, java.util.Map)}
     * method.
     *
     * <dl>
     *     <dt><b>Deploy</b></dt>
     *     <dd>
     *         <p>In a Node JS Pipeline, this stage will always be executed on a protected branch.
     *         This stage will execute after the version bump has been completed and is tasked
     *         with doing an {@code npm publish} to the publish registry.</p>
     *
     *         <p>The publish registry is determined by looking in <b>package.json</b> for the
     *         publishConfig.registry property. If this is absent, the deploy will fail. After the
     *         registry is loaded, the pipeline will attempt to login using the specified
     *         {@link #publishConfig}.</p>
     *
     *         <p>Prior to executing the deploy, changes will be pushed to
     *         the remote server. If the pipeline is behind the branch's remote, the push will
     *         fail and the deploy will stop. After changes are successfully pushed, the npm
     *         publish command will be executed with the {@link NodeJSBranch#tag} specified.
     *         On successful deploy, an email will be sent out to the {@link #admins}.</p>
     *
     *         <p>Note that the local npmrc configuration file will not affect publishing in any way.
     *         This step only considers the configuration parameters provided in {@link #publishConfig}.</p>
     *
     *         <dl><dt><b>Exceptions:</b></dt><dd>
     *         <dl>
     *             <dt><b>{@link IllegalArgumentException}</b></dt>
     *             <dd>When versionArguments.operation is provided. This is an invalid parameter.</dd>
     *         </dl></dd>
     *     </dd>
     * </dl>
     *
     * @param arguments The arguments for the Deploy stage.
     */
    protected void publishNodeJS(Map arguments = [:]) {
        if (!arguments.operation) {
            // Set the publish operation for an npm pipeline
            arguments.operation = { String stageName ->

                // Login to the publish registry
                loginToPublishRegistry()

                Boolean _isReleaseBranch = this.isReleaseBranch()
                Boolean _isPerformingRelease = this.isPerformingRelease()

                NodeJSBranch branchProps = branches.getByPattern(changeInfo.branchName)
                String npmTag = Constants.DEFAULT_NPM_NON_RELEASE_TAG
                if (_isReleaseBranch && _isPerformingRelease) {
                    npmTag = branchProps.getNpmTag()
                }

                steps.echo "Publishing package v${steps.env['PUBLISH_VERSION']} as tag ${npmTag}"

                this.publishRegistry.publish(
                    github  : this.github,
                    tag     : npmTag,
                    // we don't need to update version if it's already there
                    version : steps.env['PUBLISH_VERSION'] == this.version ? '' : steps.env['PUBLISH_VERSION']
                )
            }
        }

        // should we upload anything else by default, like npm build log?

        super.publishGeneric(arguments)
    }

    /**
     * Pseudo publish method, should be overridden by inherited classes
     * @param arguments The arguments for the publish step. {@code arguments.operation} must be
     *                        provided.
     */
    @Override
    protected void publish(Map arguments = [:]) {
        publishNodeJS(arguments)
    }

    /**
     * Tag branch when release.
     *
     * Note: npm publish will commit/tag the branch, we just need to push the changes
     */
    protected void tagBranch() {
        // should be able to guess repository and branch name
        this.github.initFromFolder()
        if (!this.github.repository) {
            throw new ScmException('Github repository is not defined and cannot be determined.')
        }
        def tag = steps.env['PUBLISH_VERSION']
        this.steps.echo "Pushing tag \"${tag}\" to \"${this.github.repository}:${this.github.branch}\"..."
        this.github.command("git push origin \"${tag}\"")
    }

    /**
     * Bump patch level version
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
            throw new NodeJSPipelineException('Unable to determine branch name to for tagging.')
        }
        publishRegistry.version(this.github, branch, 'patch')
    }

    /**
     * Signal that no more stages will be added and begin pipeline execution.
     */
    @Override
    void end(Map options = [:]) {
        super.endGeneric(options)
    }
}
