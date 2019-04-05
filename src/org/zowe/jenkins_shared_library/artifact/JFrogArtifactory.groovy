/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.artifact

import java.net.URLEncoder
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.exceptions.UnderConstructionException
import org.zowe.jenkins_shared_library.Utils

/**
 * Operating artifacts with jFrog Artifatory CLI commands or API
 */
class JFrogArtifactory implements ArtifactInterface {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * CLI config name
     */
    static final CLI_CONFIG_NAME = 'rt-server-1'

    /**
     * Repository name for snapshots
     */
    static final REPOSITORY_SNAPSHOT = 'libs-snapshot-local'

    /**
     * Repository name for releases
     */
    static final REPOSITORY_RELEASE = 'libs-release-local'

    /**
     * Artifactory URL
     */
    String url
    /**
     * Artifactory username/password credential
     */
    String usernamePasswordCredential

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def o = new JFrogArtifactory(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    JFrogArtifactory(steps) {
        this.steps = steps
    }

    /**
     * Initialize npm registry properties
     * @param   url                          the artifactory URL
     * @param   usernamePasswordCredential   Artifactory username/password credential ID
     */
    void init(Map args = [:]) {
        if (args['url']) {
            this.url = args['url']
        }
        if (args['usernamePasswordCredential']) {
            this.usernamePasswordCredential = args['usernamePasswordCredential']
        }

        if (this.steps && this.url && this.usernamePasswordCredential) {
            // prepare JFrog CLI configurations
            this.steps.withCredentials([
                this.steps.usernamePassword(
                    credentialsId: this.usernamePasswordCredential,
                    passwordVariable: 'PASSWORD',
                    usernameVariable: 'USERNAME'
                )
            ]) {
                this.steps.sh "jfrog rt config ${CLI_CONFIG_NAME}" +
                              " --url=${this.url}" +
                              " --user=\${USERNAME}" +
                              " --password=\${PASSWORD}"
            }
        }
    }

    /**
     * Get detail information of an artifact
     *
     * NOTE: this is implemented with jFrog CLI
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param   pattern            path pattern to find the artifact
     * @param   build-name         limit the search within this build name
     * @param   build-number       limit the search within this build number
     */
    Map getArtifact(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!url) {
            throw new InvalidArgumentException('url')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        Map result = [:]

        def searchOptions = ""
        def searchOptionText = ""
        if (args['build-name']) {
            // limit to build
            if (args['build-number']) {
                searchOptions = "--build=\"${args['build-name']}/${args['build-number']}\""
                searchOptionText = "in build ${args['build-name']}/${args['build-number']}"
            } else {
                searchOptions = "--build=\"${args['build-name']}\""
                searchOptionText = "in build ${args['build-name']}"
            }
        }
        this.steps.echo "Searching artifact \"${args['pattern']}\"${searchOptionText} ..."

        def resultText = this.steps.sh(
            script: "jfrog rt search ${searchOptions} \"${args['pattern']}\"",
            returnStdout: true
        ).trim()
        this.steps.echo "Raw search result:\n${resultText}"
        /**
         * Example result:
         *
         * [
         *   {
         *     "path": "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax",
         *     "props": {
         *       "build.name": "zowe-install-packaging :: master",
         *       "build.number": "38",
         *       "build.parentName": "zlux",
         *       "build.parentNumber": "570",
         *       "build.timestamp": "1537287202277"
         *     }
         *   }
         * ]
         */
        def resultJson = this.steps.readJSON text: resultText

        // validate result size
        def resultSize = resultJson.size()
        if (resultSize < 1) {
            throw new ArtifactException("Cannot find artifact \"${args['pattern']}\"${searchOptionText}")
        } else if (resultSize > 1) {
            throw new ArtifactException("Found more than one artifact (${resultSize}) of \"${args['pattern']}\"${searchOptionText}")
        }

        // fetch the first artifact
        def artifactInfo = resultJson.first()

        // validate build info
        if (!artifactInfo || !artifactInfo.path) {
            throw new ArtifactException("Failed to find artifact information (path).")
        }
        result['path'] = artifactInfo.path

        // append artifact properties
        artifactInfo.props.each { prop ->
            // think this should be a bug
            // readJSON returns value as net.sf.json.JSONArray
            // this step is a workaround
            if (prop.value.getClass().toString().endsWith('JSONArray')) {
                prop.value = prop.value.get(0)
            }
            result[prop.key] = prop.value
        }

        String readable = "Found artifact:\n"
        result.each { k, v ->
            readable += "- ${k} = ${v}\n"
        }
        this.steps.echo readable

        return result
    }

    /**
     * Get detail information of an artifact
     *
     * NOTE: this is implemented with jFrog CLI
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param   pattern            path pattern to find the artifact
     * @param   build-name         limit the search within this build name
     * @param   build-number       limit the search within this build number
     */
    Map getArtifact(String pattern, String buildName = '', String buildNumber = '') {
        return getArtifact([
            'pattern'      : pattern,
            'build-name'   : buildName,
            'build-number' : buildNumber,
        ])
    }

    /**
     * Download artifacts
     *
     * NOTE: this is implemented with jFrog CLI. The reason is sortBy in download spec is not
     *       supported by Jenkins Artifactory plugin.
     *
     * Use similar parameters like init() method and with these extra:
     *
     * @param   spec            jfrog cli download specification file. optional.
     * @param   specContent     jfrog cli download specification content text.
     *                          Will be ignored if 'spec' is defined, will be .
     *                          required if 'spec' is not defined.
     * @param   expected        if we need to check download count. optional.
     */
    void download(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        // init with arguments
        if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!args['spec'] && !args['specContent']) {
            throw new InvalidArgumentException('spec')
        }

        def tmpFile = ".tmp-down-artifact-spec-${Utils.getTimestamp()}.json"
        def specFile = args.containsKey('spec') ? args['spec'] : tmpFile
        this.steps.writeFile encoding: 'UTF-8', file: tmpFile, text: args['specContent']
        def expectedArtifacts = args.containsKey('expected') ? args['expected'] : -1

        // download
        def downloadResult = this.steps.sh(
            script: "jfrog rt dl --spec=\"${specFile}\"",
            returnStdout: true
        ).trim()

        def downloadResultObject = this.steps.readJSON(text: downloadResult)
        this.steps.echo "Artifact download result:\n" +
            "- status  : ${downloadResultObject['status']}\n" +
            "- success : ${downloadResultObject['totals']['success']}\n" +
            "- failure : ${downloadResultObject['totals']['failure']}"

        // validate download result
        if (downloadResultObject['status'] != 'success' || downloadResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact downloading has failure(s) or not successful.")

            if (expectedArtifacts > 0) {
                if (downloadResultObject['totals']['success'] != expectedArtifacts) {
                    throw new ArtifactException("Expected ${expectedArtifacts} artifact(s) to be downloaded but only got ${downloadResultObject['totals']['success']}.")
                }
            }
        }

        // remove spec file if it's temporary
        if (this.steps.fileExists(tmpFile)) {
            this.steps.sh("rm ${tmpFile}")
        }

        this.steps.echo "Artifact downloading is successful."
    }

    /**
     * Upload an artifact
     *
     * Requires these environment variables:
     * - JOB_NAME
     * - BUILD_NUMBER
     *
     * @param  pattern           pattern to find local artifact(s)
     * @param  target            target path on remote Artifactory
     */
    void uploadWithCli(Map args = [:]) {
        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!args['pattern']) {
            throw new InvalidArgumentException('pattern')
        }
        if (!args['target']) {
            throw new InvalidArgumentException('target')
        }

        def env = this.steps.env
        def buildName = env.JOB_NAME.replace('/', ' :: ')
        this.steps.echo "Uploading artifact \"${args['pattern']}\" to \"${args['target']}\"\n" +
            "- Build name   : ${buildName}" +
            "- Build number : ${env.BUILD_NUMBER}"

        // prepare build info
        this.steps.sh "jfrog rt bc '${buildName}' ${env.BUILD_NUMBER}"
        // attach git information to build info if exists
        if (this.steps.fileExists('.git/HEAD')) {
            this.steps.sh "jfrog rt bag '${buildName}' ${env.BUILD_NUMBER} ."
        }

        // upload and attach with build info
        def uploadResult = this.steps.sh(
            script: "jfrog rt u \"${args['pattern']}\" \"${args['target']}\"" +
                    " --build-name=\"${buildName}\"" +
                    " --build-number=${env.BUILD_NUMBER}" +
                    " --flat",
            returnStdout: true
        ).trim()

        def uploadResultObject = readJSON(text: uploadResult)
        this.steps.echo "Artifact upload result:\n" +
            "- status  : ${uploadResultObject['status']}\n" +
            "- success : ${uploadResultObject['totals']['success']}\n" +
            "- failure : ${uploadResultObject['totals']['failure']}"

        // validate upload result
        if (uploadResultObject['status'] != 'success' ||
            uploadResultObject['totals']['success'] != 1 || uploadResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact uploading has failure(s) or not successful.")
        }

        // add environment variables to build info
        // sh "jfrog rt bce '${buildName}' ${env.BUILD_NUMBER}"
        // publish build info
        this.steps.sh "jfrog rt bp '${buildName}' ${env.BUILD_NUMBER} --build-url=${env.BUILD_URL}"

        this.steps.echo "Artifact uploading is successful."
    }

    /**
     * Upload an artifact
     *
     * You can choose one of three ways to upload:
     *
     * - specify "spec" which pointing to a file
     * - specify "specContent" which is upload specification text
     * - specify "pattern" and "target", these information will be rendered into upload specification
     *
     * @param  spec             jfrog cli upload specification file. optional.
     * @param  specContent      jfrog cli upload specification content text.
     * @param  pattern          pattern to find local artifact(s)
     * @param  target           target path on remote Artifactory
     * @param  properties       a map of extra properties we want to add to the artifact
     */
    void upload(Map args = [:]) {
        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!url) {
            throw new InvalidArgumentException('url')
        }
        if (!usernamePasswordCredential) {
            throw new InvalidArgumentException('usernamePasswordCredential')
        }

        def spec = ''
        if (args.containsKey('spec')) {
            spec = this.steps.readFile(encoding: 'UTF-8', file: args['spec'])
        } else if (args.containsKey('specContent')) {
            spec = args['specContent']
        } else if (args.containsKey('pattern') && args.containsKey('target')) {
            def extraProperties = ''
            if (args.containsKey('properties')) {
                // convert params to querystring
                extraProperties = args['properties'].collect {
                    k, v -> k + '=' + URLEncoder.encode("${v}", 'UTF-8')
                }.join(';')
            }
            spec = """{
  "files": [
    {
      "pattern": "${args['pattern']}",
      "target": "${args['target']}",
      "props": "${extraProperties}"
    }
 ]
}"""
        } else {
            throw new InvalidArgumentException('spec')
        }

        def server = this.steps.Artifactory.newServer(
            'url'              : url,
            'credentialsId'    : usernamePasswordCredential
        )
        def buildInfo = server.upload(spec: spec)
        server.publishBuildInfo buildInfo

        this.steps.echo "Artifact uploading is successful."
    }

    /**
     * Upload an artifact
     *
     * @param  pattern           pattern to find local artifact(s)
     * @param  target            target path on remote Artifactory
     * @param  properties        a map of extra properties we want to add to the artifact
     */
    void upload(String pattern, String target, Map properties = [:]) {
        this.upload(pattern: pattern, target: target, properties: properties)
    }

    /**
     * Search artifacts with pattern
     */
    void search(Map args = [:]) {
        throw new UnderConstructionException('Under construction')
    }

    /**
     * Promote artifact
     */
    void promote(Map args = [:]) {
        throw new UnderConstructionException('Under construction')
    }
}
