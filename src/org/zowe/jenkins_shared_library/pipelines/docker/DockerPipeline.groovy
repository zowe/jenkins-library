/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.jenkins_shared_library.pipelines.docker

import groovy.util.logging.Log
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.runtime.InvokerHelper
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.jenkins_shared_library.docker.Registry
import org.zowe.jenkins_shared_library.pipelines.base.Branches
import org.zowe.jenkins_shared_library.pipelines.base.models.Stage
import org.zowe.jenkins_shared_library.pipelines.base.models.StageTimeout
import org.zowe.jenkins_shared_library.pipelines.Build
import org.zowe.jenkins_shared_library.pipelines.Constants
import org.zowe.jenkins_shared_library.pipelines.generic.arguments.ReleaseStageArguments
import org.zowe.jenkins_shared_library.pipelines.generic.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline
import org.zowe.jenkins_shared_library.pipelines.docker.arguments.*
import org.zowe.jenkins_shared_library.pipelines.docker.exceptions.*
import org.zowe.jenkins_shared_library.pipelines.docker.models.*
import org.zowe.jenkins_shared_library.scm.ScmException
import org.zowe.jenkins_shared_library.Utils

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
 * {@literal @}Library('fill this out according to your setup') import org.zowe.jenkins_shared_library.pipelines.docker.DockerPipeline
 *
 * node('pipeline-node') {
 *     // Create the runner and pass the methods available to the workflow script to the runner
 *     DockerPipeline pipeline = new DockerPipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.admins.add("userid1", "userid2", "userid3")
 *
 *     // We have extra branches which can perform release.
 *     pipeline.branches.addMap([
 *         // Please note the "releaseTag" can be separated by comma, if you want
 *         // to publish the docker image with multiple tags.
 *         [name: "special-branch", allowRelease: true, allowFormalRelease: true, isProtected: true, releaseTag: "special-tag"]
 *     ])
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setup(
 *         // Define the git configuration
 *         github: [
 *             email: 'robot-user@example.com',
 *             usernamePasswordCredential: 'robot-user'
 *         ],
 *         // Define docker registry
 *         registry: [
 *             url: 'https://registry.my-docker-server.com',
 *             usernamePasswordCredential: 'docker-user'
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
 *     // publish image to Docker registry
 *     pipeline.publish() // Provide required parameters in your pipeline
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
class DockerPipeline extends GenericPipeline {
    /**
     * A map of branches.
     */
    Branches<DockerBranch> branches = new Branches<>(DockerBranch.class)

    /**
     * Registry for publishing docker images
     */
    Registry registry

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the DockerPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pipeline = new DockerPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    DockerPipeline(steps) {
        super(steps)

        registry = new Registry(steps)
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
                releaseTag         : 'latest',
            ],
            [
                name               : 'v([0-9]+)\\.([0-9x]+)\\.([0-9x]+)?/master',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                allowFormalRelease : true,
                // formal version tags are attached automatically
                // releaseTag         : 'v$1,v$1.$2,v$1.$2.$3',
            ],
            [
                name               : 'staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                releaseTag         : 'dev',
            ],
            [
                name               : 'v([0-9]+)\\.([0-9x]+)\\.([0-9x]+)?/staging',
                isProtected        : true,
                buildHistory       : 20,
                allowRelease       : true,
                releaseTag         : 'v$1-dev,v$1.$2-dev,v$1.$2.$3-dev',
            ],
        ])
    }

    /**
     * Calls {@link org.zowe.jenkins_shared_library.pipelines.docker.DockerPipeline#setupDocker()} to setup the build.
     *
     * <p>This method adds extra initialization steps to the default setup "Init Generic Pipeline"
     * stage. The initialization will try to run #init() on the Docker Registry instance if a registry
     * is defined.</p>
     */
    void setupDocker(DockerSetupArguments arguments) throws DockerPipelineException {
        Closure initRegistry = { pipeline ->
            // init Docker registry
            if (arguments.docker) {
                pipeline.steps.echo "Init Docker registry as ${arguments.docker} ..."
                pipeline.registry.init(arguments.docker)
            }
        }
        // should we overwrite this?
        arguments.extraInit = initRegistry
        super.setupGeneric(arguments)

        // prepare default configurations
        this.defineDefaultBranches()
    }

    /**
     * Initialize the pipeline.
     *
     * @param arguments A map that can be instantiated as {@link DockerSetupArguments}
     * @see #setup(DockerSetupArguments)
     */
    void setupDocker(Map arguments = [:]) {
        DockerSetupArguments args = new DockerSetupArguments()
        InvokerHelper.setProperties(args, arguments)
        setupDocker(args)
    }

    /**
     * Pseudo setup method, should be overridden by inherited classes
     * @param arguments A map that can be instantiated as {@link DockerSetupArguments}
     */
    @Override
    protected void setup(Map arguments = [:]) {
        setupDocker(arguments)
    }

    /**
     * Creates a stage that will build a DockerPipeline package.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.docker.arguments.DockerBuildArguments} class.</p>
     *
     * <p>The stage will be created with the {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#buildGeneric(java.util.Map)}
     * method and will have the following additional operations. <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.DockerBuildArguments#operation} is not
     *     provided, the stage will default to executing {@code registry.build}.</li>
     * </ul></p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.docker.arguments.DockerBuildArguments} used to define
     *                  the stage.
     */
    void buildDocker(DockerBuildArguments arguments) {
        arguments.name = arguments.dockerFile ?: arguments.name

        if (!arguments.operation) {
            arguments.operation = { String stageName ->
                String dockerFile = arguments.dockerFile ?: this.registry.getDockerFile()

                // extract version from Dockerfile
                def versionInDockerfile = this.registry.getVersion()
                if (versionInDockerfile) {
                    this.setVersion(versionInDockerfile)
                }

                String image = registry.build([
                    dockerFile: dockerFile,
                ])
                this.steps.echo "Docker image ${image} is built successfully."
            }
        }

        super.buildGeneric(arguments)
    }

    /**
     * Creates a stage that will build a DockerPipeline package.
     *
     * @param arguments A map that can be instantiated as {@link DockerBuildArguments}
     * @see #buildDocker(DockerBuildArguments)
     */
    void buildDocker(Map arguments = [:]) {
        DockerBuildArguments args = new DockerBuildArguments()
        InvokerHelper.setProperties(args, arguments)
        buildDocker(args)
    }

    /**
     * Pseudo build method, should be overridden by inherited classes
     * @param arguments A map of arguments to be applied to the {@link BuildStageArguments} used to define
     *                  the stage.
     */
    @Override
    protected void build(Map arguments = [:]) {
        buildDocker(arguments)
    }

    /**
     * Publish a Docker image.
     *
     * <p>Default behavior of publish stage:<ul>
     * <li>A non-release build or a build on a non-release branch will publish the image with tag {@code snapshot}.</li>
     * <li>A release build on a release branch with pre-release string will generate a tag of {@code version-pre-release-string}. For example, {@code v1.2.3-RC1}.</li>
     * <li>A formal release build on a formal release branch without pre-release string will generate serial version tags. For example, {@code "v1"} {@code "v1.2"} and {@code "v1.2.3"}.</li>
     * </ul></p>
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments} class.</p>
     *
     * <p>The stage will be created with the
     * {@link org.zowe.jenkins_shared_library.pipelines.generic.GenericPipeline#publishGeneric(java.util.Map)} method and will
     * have the following additional operations: <ul>
     *     <li>If {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments#operation} is not
     *     provided, this method will default to executing {@code docker push} with defined releaseTag of the branch.</li>
     * </ul>
     * </p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.jenkins_shared_library.pipelines.generic.arguments.PublishStageArguments} used to define
     *                  the stage.
     */
    protected void publishDocker(DockerPublishArguments arguments) {
        arguments.name = arguments.image ?: arguments.name

        if (!arguments.operation) {
            // Set the publish operation for a Docker pipeline
            arguments.operation = { String stageName ->
                String imageId = this.registry.getImageId()
                String imageName = arguments.image ?: this.registry.getImage()

                Boolean _isReleaseBranch = this.isReleaseBranch()
                Boolean _isFormalReleaseBranch = this.isFormalReleaseBranch()
                Boolean _isPerformingRelease = this.isPerformingRelease()
                String _preReleaseString = this.getPreReleaseString()

                String tags = ''
                if (_isReleaseBranch && _isPerformingRelease) {
                    tags = this.getBranchTag()

                    // attach version tags
                    String version = this.getVersion()
                    if (version) {
                        Map versionTrunks = Utils.parseSemanticVersion(version)

                        // this is real formal release
                        if (_isFormalReleaseBranch && !_preReleaseString) {
                            tags = (tags ? tags + ',' : '') +
                                "v${versionTrunks['major']}," +
                                "v${versionTrunks['major']}.${versionTrunks['minor']}," +
                                "v${versionTrunks['major']}.${versionTrunks['minor']}.${versionTrunks['patch']}"
                        } else if (_preReleaseString) {
                            tags = (tags ? tags + ',' : '') +
                                "v${versionTrunks['major']}.${versionTrunks['minor']}.${versionTrunks['patch']}-${_preReleaseString}"
                        }
                    }
                }
                if (!tags) {
                    tags = Constants.DEFAULT_DOCKER_NON_RELEASE_TAG
                }

                steps.echo "Publishing docker image ${imageId} as ${imageName}:${tags}"

                this.registry.publish(
                    image   : imageName,
                    tags    : tags,
                )
            }
        }

        super.publishGeneric(arguments)
    }

    /**
     * Publish a Docker image.
     *
     * @param arguments A map that can be instantiated as {@link DockerPublishArguments}
     * @see #publishDocker(DockerPublishArguments)
     */
    void publishDocker(Map arguments = [:]) {
        // if the Arguments class is not base class, the {@code "arguments as SomeStageArguments"} statement
        // has problem to set values of properties defined in super class.
        DockerPublishArguments args = new DockerPublishArguments()
        InvokerHelper.setProperties(args, arguments)
        publishDocker(args)
    }

    /**
     * Pseudo publish method, should be overridden by inherited classes
     * @param arguments The arguments for the publish step. {@code arguments.operation} must be
     *                        provided.
     */
    @Override
    protected void publish(Map arguments = [:]) {
        publishDocker(arguments)
    }

    /**
     * This method overrides and perform version bump on Dockerfile.
     *
     * <p>By default, the {@code version} LABEL defined in {@code Dockerfile} will be bumped with patch level.</p>
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
            throw new DockerPipelineException('Unable to determine branch name for version bump.')
        }
        String dockerFile = this.registry.getDockerFile()
        if (!dockerFile) {
            throw new DockerPipelineException('Unable to determine docker file for version bump.')
        }

        this.registry.version(this.github, branch, 'patch')
    }
}
