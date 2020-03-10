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
import org.codehaus.groovy.runtime.InvokerHelper
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
 * Extends the functionality available in the {@link jenkins_shared_library.pipelines.generic.GenericPipeline} class.
 * This class adds more advanced functionality to build, test, and deploy your JavaScript/TypeScript application.
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
 *     // We have extra branches which can perform release.
 *     pipeline.branches.addMap([
 *         [name: "lts-stable", allowRelease: true, allowFormalRelease: true, isProtected: true, npmTag: "lts-stable"]
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
 *     // Run a SonarQube code scan
 *     pipeline.sonarScan()
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
     * A map of branches.
     */
    Branches<NodeJSBranch> branches = new Branches<>(NodeJSBranch.class)

    /**
     * Registry for publishing npm package
     */
    Registry publishRegistry

    /**
     * If we have a customized node.js version to use for this pipeline
     */
    String nodeJsVersion


    /**
     * Path to nvm.sh.
     */
    String nvmInitScript

    /**
     * Registries for installing npm dependencies
     */
    List<Registry> installRegistries = []

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
     * Login to npm publish registry
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
     * Login to npm install registries
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
     * Run shell script under nvm environment
     *
     * @param  script      which shell script to execute
     */
    String nvmShell(script) {
        if (this.nvmInitScript && this.nodeJsVersion) {
            steps.sh "set +x\n. ${this.nvmInitScript}\nnvm use ${this.nodeJsVersion}\nset -x\n${script}"
        } else {
            steps.sh script
        }
    }

    /**
     * Calls {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#setupGeneric()} to setup the build.
     *
     * <p>This method adds extra initialization steps to the default setup "Init Generic Pipeline"
     * stage. The initialization will try to extract package information, like name, version, etc
     * from package.json. And if there is publish registry defined, it will try to run #init() on
     * the Registry instance. Similar to install registries. If there are install registries defined,
     * and credential(s) are provided, the initliazation will try to login to the registries so we
     * can perform npm install without issues.</p>
     *
     * @Stages
     * This method adds one stage to the build:
     *
     * <dl>
     *     <dt><b>Install Node Package Dependencies</b></dt>
     *     <dd>
     *         <p>
     *             This step will install all your package dependencies via {@code npm install} or {@code npm ci}. Prior to install
     *             the stage will login to any registries specified in the {@link #installRegistries} array.
     *         </p>
     *         <dl>
     *             <dt><b>Exceptions:</b></dt>
     *             <dd>
     *                 <dl>
     *                     <dt><b>{@link NodeJSPipelineException}</b></dt>
     *                     <dd>When two default registries, a registry that omits a url, are specified.</dd>
     *                     <dd>When a login to a registry fails. <b>Note:</b> Failure to logout of a
     *                         registry will not result in a failed build.</dd>
     *                     <dt><b>{@link Exception}</b></dt>
     *                     <dd>When a failure to install dependencies occurs.</dd>
     *                     <dd>When the git folder is not clean after npm install if arguments.exitIfFolderNotClean is true.</dd>
     *                 </dl>
     *             </dd>
     *         </dl>
     *     </dd>
     *     <dt><b>Lint</b></dt>
     *     <dd>
     *         <p>
     *             This step will run {@code npm run lint} if the command exists. You can skip this
     *             stage by setting arguments.disableLint to true.
     *         </p>
     *         <dl>
     *             <dt><b>Exceptions:</b></dt>
     *             <dd>
     *                 <dl>
     *                     <dt><b>{@link Exception}</b></dt>
     *                     <dd>When the linting failed.</dd>
     *                 </dl>
     *             </dd>
     *         </dl>
     *     </dd>
     *     <dt><b>Audit</b></dt>
     *     <dd>
     *         <p>
     *             This step will perform dependency vulnerability audit with command {@code npm audit}.
     *         </p>
     *         <dl>
     *             <dt><b>Exceptions:</b></dt>
     *             <dd>
     *                 <dl>
     *                     <dt><b>{@link Exception}</b></dt>
     *                     <dd>When the audit check failed. Set arguments.ignoreAuditFailure to true if you want to ignore the audit error.</dd>
     *                 </dl>
     *             </dd>
     *         </dl>
     *     </dd>
     * </dl>
     */
    void setupNodeJS(NodeJSSetupStageArguments arguments) throws NodeJSPipelineException {
        Closure initRegistry = { pipeline ->
            // init registries
            if (arguments.publishRegistry) {
                pipeline.steps.echo 'Init publish registry ...'
                pipeline.publishRegistry.init(arguments.publishRegistry)
                // try to extract publish registry from package.json
                pipeline.publishRegistry.initFromPackageJson()
                pipeline.steps.echo "- ${pipeline.publishRegistry.scope ? '@' + pipeline.publishRegistry.scope + ':' : ''}${pipeline.publishRegistry.registry ?: '(WARNING: undefined publish registry)'}"
            }
            if (arguments.installRegistries) {
                pipeline.steps.echo 'Init install registries ...'
                for (Map config : arguments.installRegistries) {
                    Registry registry = new Registry(steps)
                    registry.init(config)
                    pipeline.steps.echo "- ${registry.scope ? '@' + registry.scope + ':' : ''}${registry.registry}"
                    pipeline.installRegistries.push(registry)
                }
            }

            // try to login to npm install registries
            pipeline.loginToInstallRegistries()
            // init package info from package.json
            pipeline.packageInfo = pipeline.publishRegistry.getPackageInfo()
            if (!pipeline.packageInfo['versionTrunks'] ||
                pipeline.packageInfo['versionTrunks']['prerelease'] ||
                pipeline.packageInfo['versionTrunks']['metadata']) {
                throw new NodeJSPipelineException('Version defined in package.json shouldn\'t have pre-release string or metadata, pipeline will adjust based on branch and build parameter.')
            }
            // version could be used to publish artifact
            pipeline.setVersion(pipeline.packageInfo['version'])
            pipeline.steps.echo "Package information: ${pipeline.getPackageName()} v${pipeline.getVersion()}"

            // do we want to use default version of node.js on the build container?
            if (arguments.nodeJsVersion) {
                if (!arguments.nvmInitScript || !pipeline.steps.fileExists(arguments.nvmInitScript)) {
                    throw new NodeJSPipelineException("NVM is required to switch node.js version");
                }
                pipeline.nodeJsVersion = arguments.nodeJsVersion
                pipeline.nvmInitScript = arguments.nvmInitScript
                pipeline.steps.echo "Pipeline will use node.js ${pipeline.nodeJsVersion} to build and test"
                pipeline.steps.sh "set +x\n. ${pipeline.nvmInitScript}\nnvm install ${pipeline.nodeJsVersion}\nnpm install npm -g\nnpm install yarn -g"
            }
        }
        // should we overwrite this?
        arguments.extraInit = initRegistry
        super.setupGeneric(arguments)

        // prepare default configurations
        this.defineDefaultBranches()

        // this stage should always happen for node.js project?
        createStage(name: 'Install Node Package Dependencies', stage: {
            if (steps.fileExists('yarn.lock')) {
                nvmShell("yarn install")
            } else {
                // we save audit part to next stage
                if (arguments.alwaysUseNpmInstall) {
                    nvmShell("npm install --no-audit")
                } else {
                    if (steps.fileExists('package-lock.json')) {
                        // if we have package-lock.json, try to use everything defined in that file
                        nvmShell("npm ci")
                    } else {
                        nvmShell("npm install --no-audit")
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

        if (!arguments.disableLint) {
            createStage(
                name: 'Lint',
                stage: {
                    nvmShell('npm run lint')
                },
                timeout: arguments.lint,
                shouldExecute: {
                    boolean shouldExecute = true

                    def lintDefined = this.packageInfo && this.packageInfo['scripts'] && this.packageInfo['scripts'].contains('lint')
                    steps.echo lintDefined ? 'lint is defined in package.json' : 'lint is NOT defined in package.json'

                    return shouldExecute && lintDefined
                }
            )
        }

        if (!arguments.disableAudit) {
            createStage(
                name: 'Audit',
                stage: {
                    try {
                        this.publishRegistry.audit()
                    } catch (e) {
                        if (arguments.ignoreAuditFailure) {
                            steps.echo "WARNING: npm audit failed with error \"${e}\" but is ignored."
                        } else {
                            throw e
                        }
                    }
                },
                isSkippable: true,
                timeout: arguments.audit
            )
        }
    }

    /**
     * Initialize the pipeline.
     *
     * @param arguments A map that can be instantiated as {@link NodeJSSetupStageArguments}
     * @see #setup(NodeJSSetupStageArguments)
     */
    void setupNodeJS(Map arguments = [:]) {
        // if the Arguments class is not base class, the {@code "arguments as SomeStageArguments"} statement
        // has problem to set values of properties defined in super class.
        NodeJSSetupStageArguments args = new NodeJSSetupStageArguments()
        InvokerHelper.setProperties(args, arguments)
        setupNodeJS(args)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param arguments A map that can be instantiated as {@link NodeJSSetupStageArguments}
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
     * </ul></p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.BuildStageArguments} used to define
     *                  the stage.
     */
    void buildNodeJS(Map arguments = [:]) {
        if (!arguments.operation) {
            arguments.operation = { String stageName ->
                nvmShell("npm run build")
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
            arguments.operation = { String stageName ->
                nvmShell("npm run test")
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
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#testGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments#operation} is not
     *     provided, this method will default to executing {@code npm publish} with defined npmTag of the branch.</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments} used to define
     *                  the stage.
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
                String npmTag = ''
                if (branchProps && _isReleaseBranch && _isPerformingRelease) {
                    npmTag = branchProps.getNpmTag()
                }
                if (!npmTag) {
                    npmTag = Constants.DEFAULT_NPM_NON_RELEASE_TAG
                }

                steps.echo "Publishing package v${steps.env['PUBLISH_VERSION']} as tag ${npmTag}"

                this.publishRegistry.publish(
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
     * This method overrides and perform version bump on JavaScript project.
     *
     * <p>By default, the {@code version} defined in {@code package.json} will be bumped with command
     * {@code npm version patch}.</p>
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
            throw new NodeJSPipelineException('Unable to determine branch name to for version bump.')
        }
        publishRegistry.version(this.github, branch, 'patch')
    }
}
