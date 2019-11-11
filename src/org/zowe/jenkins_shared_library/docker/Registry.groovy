/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.docker

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.scm.GitHub
import org.zowe.jenkins_shared_library.Utils

/**
 * Methods to handle Docker registry actions.
 *
 * @Example
 * <pre>
 *     def dockerRegistry = new Registry(this)
 *     // init docker registry
 *     dockerRegistry.init(
 *         usernamePasswordCredential : 'my-docker-registry-credential',
 *         url                        : 'https://registry.docker-image.com/',
 *     )
 * </pre>
 *
 * <p>This class rely on the version defined in Dockefile. Based on Dockerfile reference,
 * the proper way to define version for a Dockefile is:</p>
 *
 * <pre>
 * LABEL version="1.0.0"
 * </pre>
 *
 * <p>Please note, we only support semantic versioning and the version label line cannot be
 * combined with other label key=value pairs.</p>
 *
 * @link https://docs.docker.com/engine/reference/builder/#label
 * @link https://semver.org/
 */
@Log
class Registry {
    /**
     * Constant of default {@code Dockerfile} file path and name
     *
     * @Default {@code "Dockerfile"}
     */
    public static final String DEFAULT_DOCKERFILE = 'Dockerfile'

    /**
     * Default Docker image tag.
     *
     * @Default {@code "latest"}
     */
    public static final String DEFAULT_IMAGE_TAG = 'latest'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Docker registry url
     *
     * @Default {@code ''}
     */
    String url = ''

    /**
     * Docker registry without https:// or http://
     *
     * @Default {@code ''}
     */
    String _url = ''

    /**
     * Jenkins credential ID for Docker registry username/password(token)
     */
    String usernamePasswordCredential

    /**
     * File path and name of {@code Dockerfile}.
     *
     * @Default {@link #DEFAULT_DOCKERFILE}
     */
    String dockerFile = DEFAULT_DOCKERFILE

    /**
     * Docker image name
     */
    String image

    /**
     * Temporary image name
     */
    String _image

    /**
     * Image version extracted from Dockerfile
     */
    String version

    /**
     * Docker image tags, can be multiple
     *
     * @Default {@link #DEFAULT_IMAGE_TAG}
     */
    String[] tags = [DEFAULT_IMAGE_TAG]

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def dockerRegistry = new Registry(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Registry(steps) {
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize Docker registry properties.
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   url                         the registry URL
     * @param   usernamePasswordCredential  Jenkins credential ID for Docker registry username/password(token). Optional.
     * @param   dockerFile                  {@code Dockerfile} file path and name. Optional, default is {@link #DEFAULT_DOCKERFILE}.
     * @param   image                       docker image name
     * @param   tag                         docker image tag
     * @param   tags                        docker image tags, can be multiple
     */
    void init(Map args = [:]) {
        String origDockerFile = this.dockerFile
        if (args.containsKey('dockerFile')) {
            this.dockerFile = args['dockerFile']
        }
        if (!this.dockerFile) {
            this.dockerFile = DEFAULT_DOCKERFILE
        }
        // try to extract version from Dockerfile
        if (origDockerFile != this.dockerFile || !this.version) {
            this.version = this.steps.sh(
                    script: "cat \"${this.dockerFile}\" | " +
                            "grep -i -e 'label\\s\\+version=' | " +
                            "awk -F= '{print \$2};' | " +
                            "sed \"s/[^0-9\\.']/ /g\"",
                    returnStdout: true
                ).trim()
            if (this.version) {
                this.steps.echo "Found version defined in ${this.dockerFile}: ${this.version}."
            } else {
                throw new InvalidArgumentException('dockerFile', "Cannot find version label in ${this.dockerFile}")
            }
        }

        if (args.containsKey('url')) {
            this.url = args['url']
        }
        // normalize registry url
        if (this.url) {
            if (!this.url.endsWith('/')) {
                this.url += '/'
            }
            this.url = this.url.toLowerCase()

            if (this.url.startsWith('https://')) {
                this._url = this.url.substring(8)
            } else if (this.url.startsWith('http://')) {
                this._url = this.url.substring(7)
            } else {
                this._url = this.url
            }
        }
        if (args.containsKey('usernamePasswordCredential')) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']
        }
        if (args.containsKey('image')) {
            this.image = args['image']
        }
        if (args.containsKey('tag')) {
            this.tags = args['tag']
        }
        if (args.containsKey('tags')) {
            if (args['tags'] instanceof String) {
                if (args['tags']) {
                    this.tags = args['tags'].split(/,/)
                }
            } else if (args['tags'] instanceof ArrayList || args['tags'] instanceof String[]) {
                if (args['tags'].size() > 0) {
                    this.tags = args['tags']
                }
            } else if (args['tags']) {
                throw new InvalidArgumentException('tags', "tags with type ${args['tags'].getClass()} is not accepted")
            }
        }
        if (this.tags.size() == 0) {
            this.tags = [DEFAULT_IMAGE_TAG]
        }
    }

    /**
     * Return full image name
     *
     * @param  tag       docker image tag name
     */
    String getFullImageName(String tag) throws InvalidArgumentException {
        // validate arguments
        if (!this.image) {
            throw new InvalidArgumentException('image')
        }
        if (!tag) {
            throw new InvalidArgumentException('tag')
        }

        return "${this._url}${this.image}:${tag}".toString()
    }

    /**
     * Return the image ID from last build
     */
    String getImageId() {
        return this._image
    }

    /**
     * Build docker image.
     *
     * @Example
     * <pre>
     *     // build docker image from ./my-sub-folder/Dockerfile
     *     // it returns image ID (hash)
     *     def imageId = dockerRegistry.build(
     *         dockerFile   : './my-sub-folder/Dockerfile'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     */
    String build(Map args = [:]) throws InvalidArgumentException, DockerException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        // validate arguments
        if (!this.dockerFile) {
            throw new InvalidArgumentException('dockerFile')
        }
        if (!this.steps.fileExists(this.dockerFile)) {
            throw new DockerException("Couldn't find ${this.dockerFile}.")
        }

        String dockerFilePath = this.steps.sh(script: "dirname \"${this.dockerFile}\"", returnStdout: true).trim()
        String dockerFileName = this.steps.sh(script: "basename \"${this.dockerFile}\"", returnStdout: true).trim()

        this.steps.echo "Building Docker image from ${dockerFilePath}/${dockerFileName} ..."
        this.steps.dir(dockerFilePath) {
            def tmpTag = 'zowe_docker_tmp_' + Utils.getTimestamp()
            this.steps.sh "docker build -t \"${tmpTag}\" -f ${dockerFileName} ."
            this._image = this.steps.sh(script: "docker images | grep \"${tmpTag}\" | awk '{print \$3};'", returnStdout: true).trim()
        }

        return this._image
    }

    /**
     * Build docker image.
     *
     * @see #build(Map)
     */
    String build(String dockerFile) {
        return this.build([
            'dockerFile'    : dockerFile,
        ])
    }

    /**
     * Remove last build image
     */
    void clean() {
        // validate arguments
        if (!this._image) {
            throw new DockerException('Can only clean after build.')
        }

        this.steps.sh "docker rmi -f \"${this._image}\""
    }

    /**
     * Tag the build image
     *
     * @param    tag                  tag name
     */
    String tag(String tag) {
        // validate arguments
        if (!this._image) {
            throw new DockerException('Can only tag after build.')
        }
        if (!tag) {
            throw new InvalidArgumentException('tag')
        }

        String fullImageName = this.getFullImageName(tag)

        this.steps.sh "docker tag \"${this._image}\" \"${fullImageName}\""

        return fullImageName
    }

    /**
     * Publish the image to registry.
     *
     * @Example
     * <pre>
     *     // publish last build image to registry
     *     // the image will also be tagged as latest, v1.x and v1.2.3
     *     dockerRegistry.publish(
     *         image                      : 'my-user/my-image',
     *         tags                       : ['latest', 'v1.x', 'v1.2.3'],
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     */
    void publish(Map args = [:]) throws InvalidArgumentException, DockerException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        // validate arguments
        if (!this.image) {
            throw new InvalidArgumentException('image')
        }
        if (!this._image) {
            throw new DockerException('Can only publish after build.')
        }
        if (this.tags.size() == 0) {
            throw new InvalidArgumentException('tags')
        }

        this.within {
            this.tags.each {
                def tag = it.trim()
                if (tag) {
                    def fullImageName = this.tag(it)
                    this.steps.sh "docker push \"${fullImageName}\""
                }
            }
        }
    }

    /**
     * Publish the image to registry.
     *
     * @see #publish(Map)
     */
    void publish(String image, String tags = '') {
        this.publish([
            'image'     : image,
            'tags'      : tags,
        ])
    }

    /**
     * Publish the image to registry.
     *
     * @see #publish(Map)
     */
    void publish(String image, String[] tags) {
        this.publish([
            'image'     : image,
            'tags'      : tags,
        ])
    }

    /**
     * Execute scripts within proper docker registry authentication
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method:
     *
     * @param   body              a CLosure scripts to run within the registrry
     */
    void within(Map args, Closure body) throws DockerException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        if (this.usernamePasswordCredential) {
            this.steps.withCredentials([
                this.steps.usernamePassword(
                    credentialsId: this.usernamePasswordCredential,
                    passwordVariable: 'PASSWORD',
                    usernameVariable: 'USERNAME'
                )
            ]) {
                this.steps.sh "docker login -u \${USERNAME} -p \${PASSWORD} ${this.url}"

                body()
            }
        } else {
            body()
        }
    }

    /**
     * Execute scripts within proper docker registry
     *
     * @see #within(Map, Closure)
     */
    void within(Closure body) {
        this.within([:], body)
    }


    /**
     * Declare a new version of Docker image.
     *
     * @Note This task will bump the docker image version defined in Dockerfile, commit the change, and push
     * to GitHub. The commit is signed-off.
     *
     * @Example
     * <pre>
     *     def github = new org.zowe.jenkins_shared_library.scm.GitHub(this)
     *     // bump patch version on master branch
     *     dockerRegistry.version(
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
     * @param branch         which branch to release
     * @param version        what kind of version bump we should make
     */
    void version(Map args = [:]) throws InvalidArgumentException, DockerException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }

        // validate arguments
        if (!this.dockerFile) {
            throw new InvalidArgumentException('dockerFile')
        }
        if (!this.steps.fileExists(this.dockerFile)) {
            throw new DockerException("Couldn't find ${this.dockerFile}.")
        }
        if (!args['github']) {
            throw new InvalidArgumentException('github')
        }
        if (!args['branch']) {
            throw new InvalidArgumentException('branch')
        }
        def bumpLevel = args.containsKey('version') ? args['version'] : 'PATCH'
        def oldVersion = this.getVersion()
        if (!oldVersion) {
            throw new DockerException("The Dockerfile doesn't have version defined.")
        }
        def oldVersionTrunks = Utils.parseSemanticVersion(oldVersion)
        def newVersion = Utils.interpretSemanticVersionBump(oldVersionTrunks, bumpLevel)

        // get temp folder for cloning
        def tempFolder = ".tmp-docker-registry-${Utils.getTimestamp()}"
        def oldBranch = args['github'].getBranch()
        def oldFolder = args['github'].getFolder()

        this.steps.echo "Cloning ${args['branch']} into ${tempFolder} ..."
        // clone to temp folder
        args['github'].cloneRepository([
            'branch'   : args['branch'],
            'folder'   : tempFolder
        ])

        // Update and commit changes
        this.steps.echo "Making a \"${version}\" version bump (${oldVersion} => ${newVersion}) ..."
        this.steps.dir(tempFolder) {
            this.steps.sh "sed -E 's#[Ll][Aa][Bb][Ee][Ll] +version=.*#LABEL version=\"${newVersion}\"#' ${this.dockerFile} > ${this.dockerFile}.tmp && " +
                          "mv ${this.dockerFile}.tmp ${this.dockerFile}"
        }
        args['github'].command('git diff')
        args['github'].commit(newVersion)

        // push version changes
        this.steps.echo "Pushing ${args['branch']} to remote ..."
        args['github'].push()
        if (!args['github'].isSynced()) {
            throw new DockerException('Branch is not synced with remote after npm version.')
        }

        // remove temp folder
        this.steps.echo "Removing temporary folder ${tempFolder} ..."
        this.steps.sh "rm -fr ${tempFolder}"

        // set values back
        args['github'].setBranch(oldBranch)
        args['github'].setFolder(oldFolder)
    }

    /**
     * Declare a new version of docker image.
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
