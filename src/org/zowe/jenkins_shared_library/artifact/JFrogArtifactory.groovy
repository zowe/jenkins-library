/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.artifact

import groovy.util.logging.Log
import java.net.URLEncoder
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.exceptions.UnderConstructionException
import org.zowe.jenkins_shared_library.Utils

/**
 * Operating artifacts with jFrog Artifatory CLI commands or Jenkins Plugin.
 *
 * @Example
 * <pre>
 *     def jfrog = new JFrogArtifactory(this)
 *     jfrog.init(
 *         url: 'https://zowe.jfrog.io/zowe',
 *         usernamePasswordCredential: 'my-artifactory-credential'
 *     )
 *     jfrog.upload(
 *         pattern : 'build/result/of/my-project.zip',
 *         target  : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip'
 *     )
 * </pre>
 */
@Log
class JFrogArtifactory implements ArtifactInterface {
    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * CLI config name.
     *
     * @Default {@code "rt-server-1"}
     */
    static final CLI_CONFIG_NAME = 'rt-server-1'

    /**
     * Repository name for snapshots.
     *
     * @Default {@code "libs-snapshot-local"}
     */
    static final REPOSITORY_SNAPSHOT = 'libs-snapshot-local'

    /**
     * Repository name for releases.
     *
     * @Default {@code "libs-release-local"}
     */
    static final REPOSITORY_RELEASE = 'libs-release-local'

    /**
     * Artifactory URL.
     *
     * @Example {@code https://zowe.jfrog.io/zowe}
     */
    String url

    /**
     * Artifactory username/password credential id defined on Jenkins.
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
     * def jfrog = new JFrogArtifactory(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    JFrogArtifactory(steps) {
        this.steps = steps
    }

    /**
     * Initialize npm registry properties.
     *
     * @Note The below parameters are supported keys of the {@code args} Map.
     *
     * @param   url                          the artifactory URL. For example {@code "https://zowe.jfrog.io/zowe"}.
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
                this.steps.sh "jfrog config add ${CLI_CONFIG_NAME}" +
                              " --overwrite" +
                              " --interactive=false" +
                              " --artifactory-url=${this.url}" +
                              " --user=\${USERNAME}" +
                              " --password=\${PASSWORD}"
            }
        }
    }

    /**
     * Get list of artifacts match the criteria
     *
     * @Note This method is implemented with jFrog CLI.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   pattern            path pattern to find the artifact. For example: {@code "lib-snapshot-local/path/to/artifacts/*.zip"}
     * @param   build-name         limit the search within this build name. Optional.
     * @param   build-number       limit the search within this build number. Optional.
     * @return                     a net.sf.json.JSONArray with the artifacts list
     */
    def getArtifacts(Map args = [:]) throws InvalidArgumentException {
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
        def buildNumber
        if (args['build-number']) {
            buildNumber = "/${args['build-number']}"
        }
        else {
            // we need to get latest build number from the job specified
            def resultText = this.steps.sh(
                script: "jfrog rt curl -XGET \"/api/build/${args['build-name'].replace('/', ' :: ')}\"",
                returnStdout: true
            ).trim()
            def results = this.steps.readJSON text: resultText
            buildNumber = results["buildsNumbers"][0]["uri"]
        }
        def searchOptions = ""
        def searchOptionText = ""
        if (args['build-name']) {
            // limit to build
            searchOptions = "--build=\"${args['build-name'].replace('/', ' :: ')}${buildNumber}\""
            searchOptionText = "in build ${args['build-name']}${buildNumber}"
        }
        this.steps.echo "Searching artifact \"${args['pattern']}\"${searchOptionText} ..."

        def resultText = this.steps.sh(
            script: "jfrog rt search ${searchOptions} \"${args['pattern']}\"",
            returnStdout: true
        ).trim()
        // this.steps.echo "Raw search result:\n${resultText}"
        /*
        Example result:
        [
          {
            "path": "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax",
            "props": {
              "build.name": "zowe-install-packaging :: master",
              "build.number": "38",
              "build.parentName": "zlux",
              "build.parentNumber": "570",
              "build.timestamp": "1537287202277"
            }
          }
        ]
        */
        def results = this.steps.readJSON text: resultText
        def resultSize = results.size()

        String readable = "Found ${resultSize} artifact(s):\n"
        results.each { it ->
            readable += "- ${it.path}\n"
        }
        this.steps.echo readable

        return results
    }

    /**
     * Get detail information of an artifact.
     *
     * @Note This method is implemented with jFrog CLI.
     *
     * <p><strong>Example output:</strong><ul>
     * <li>{@code path} - {@code "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax"}</li>
     * <li>{@code build.name} - {@code "zowe-install-packaging :: master"}</li>
     * <li>{@code build.number} - {@code "38"}</li>
     * <li>{@code build.timestamp} - {@code "1537287202277"}</li>
     * </ul></p>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   pattern            path pattern to find the artifact. For example: {@code "lib-snapshot-local/path/to/artifacts/*.zip"}
     * @param   build-name         limit the search within this build name. Optional.
     * @param   build-number       limit the search within this build number. Optional.
     * @return                     a Map with the artifact information. Must include {@code path} key.
     * @throws ArtifactException   Cannot find or find more than one artifacts.
     */
    Map getArtifact(Map args = [:]) throws InvalidArgumentException, ArtifactException {
        def results = this.getArtifacts(args)

        // validate result size
        def resultSize = results.size()
        if (resultSize < 1) {
            throw new ArtifactException("Cannot find artifact \"${args['pattern']}\"")
        } else if (resultSize > 1) {
            throw new ArtifactException("Found more than one artifact (${resultSize}) of \"${args['pattern']}\"")
        }

        // fetch the first artifact
        def artifactInfo = results.first()
        // validate build info
        if (!artifactInfo || !artifactInfo.path) {
            throw new ArtifactException("Failed to find artifact information (path).")
        }

        Map result = [:]
        result['path'] = artifactInfo.path

        // append artifact properties
        artifactInfo.props.each { prop ->
            // think this should be a bug
            // readJSON returns value as net.sf.json.JSONArray
            // this step is a workaround
            def val = prop.value
            if (val.getClass().toString().endsWith('JSONArray')) {
                val = val.get(0)
            }
            result[prop.key] = val
        }

        String readable = "Found artifact:\n"
        result.each { k, v ->
            readable += "- ${k} = ${v}\n"
        }
        this.steps.echo readable

        return result
    }

    /**
     * Get detail information of an artifact.
     *
     * @see #getArtifact(Map)
     */
    Map getArtifact(String pattern, String buildName = '', String buildNumber = '') {
        return getArtifact([
            'pattern'      : pattern,
            'build-name'   : buildName,
            'build-number' : buildNumber,
        ])
    }

    /**
     * Get build information from Artifactory.
     *
     * @Note This method is implemented with jFrog API call.
     *
     * @param   build-name         limit the search within this build name. Required.
     * @param   build-number       limit the search within this build number. Required.
     * @return                     full build information JSON object
     * @throws ArtifactException   Cannot find the build or other API failures.
     */
    Map getBuildInfo(Map args = [:]) throws InvalidArgumentException, ArtifactException {
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
        if (!args['build-name']) {
            throw new InvalidArgumentException('build-name')
        }
        if (!args['build-number']) {
            throw new InvalidArgumentException('build-number')
        }

        // FIXME: this could be risky if build name including non-ASCII characters
        def encodedBuildName = args['build-name'].replace('/', ' :: ').replace(' ', '%20')
        this.steps.echo "Fetching build information of \"${encodedBuildName}/${args['build-number']}\" ..."

        def resultText = null
        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId: this.usernamePasswordCredential,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )
        ]) {
            resultText = this.steps.sh(
                script: "curl -u \"\${USERNAME}:\${PASSWORD}\" -sS" +
                        " \"${this.url}/api/build/${encodedBuildName}/${args['build-number']}\"",
                returnStdout: true
            ).trim()
        }
        if (!resultText) {
            throw new ArtifactException("invalid Artifacory API response \"${resultText}\"")
        }
        // this.steps.echo "Raw search result:\n${resultText}"
        /*
        Example result:
        {
            "buildInfo" : {
                "version" : "1.0.1",
                "name" : "zowe-install-packaging :: staging",
                "number" : "218",
                "type" : "GENERIC",
                "buildAgent" : {
                    "name" : "Pipeline",
                    "version" : ""
                },
                "agent" : {
                    "name" : "Jenkins",
                    "version" : "2.164.2"
                },
                "started" : "2019-09-19T16:19:28.927+0000",
                "durationMillis" : 245379,
                "principal" : "anonymous",
                "artifactoryPrincipal" : "giza-jenkins",
                "artifactoryPluginVersion" : "3.3.0",
                "url" : "https://wash.zowe.org:8443/job/zowe-install-packaging/job/staging/218/",
                "vcs" : [ {
                    "revision" : "07bc1f8de018e86a3fe099710ecf7083cd690887",
                    "url" : "https://github.com/zowe/jenkins-library.git",
                    "empty" : false
                }, {
                    "revision" : "d6b2a3fe45de71434ad13a133dfb3f433ccb6473",
                    "url" : "https://github.com/zowe/zowe-install-packaging.git",
                    "empty" : false
                } ],
                "modules" : [ {
                    "id" : "zowe-install-packaging :: staging",
                    "artifacts" : [ {
                        "type" : "pax",
                        "sha1" : "7390e4051add42a12d36db104c8d3afd59b6bb3a",
                        "sha256" : "e384342f73b2de01dd6d6106349270697f35e7976b640bbb9393098b24afa799",
                        "md5" : "738d03c75e44d417438f50e6682671fa",
                        "name" : "zowe.pax"
                    } ],
                    "dependencies" : [ ]
                } ],
                "buildDependencies" : [ ]
            },
            "uri" : "https://zowe.jfrog.io/zowe/api/build/zowe-install-packaging%20::%20staging/218"
        }
        */
        def result = this.steps.readJSON text: resultText
        if (result && result.containsKey('errors')) {
            /*
            Example error response:
            {
                "errors" : [ {
                    "status" : 404,
                    "message" : "No build was found for build name: zowe-install-packaging :: staging1, build number: 2111 "
                } ]
            }
            */
           def firstError = result.errors.first()
           if (firstError && firstError.message) {
               throw new ArtifactException("Artifactory API error: ${firstError.message}")
           } else {
               throw new ArtifactException("Artifactory API error: ${firstError}")
           }
        } else if (result && result.containsKey('buildInfo')) {
            // the follwing two lines are removed from buildInfo
            //  "vcsRevision" : "d6b2a3fe45de71434ad13a133dfb3f433ccb6473",
            //  "vcsUrl" : "https://github.com/zowe/zowe-install-packaging.git",
            // thus need to pick and generate vcs from scratch if not available
            def vcsList = result.buildInfo.vcs
            if (vcsList) {
                for (def vcs : vcsList) {
                    if (args['vcs-url-keyword'] && vcs.url.contains(args['vcs-url-keyword'])) {
                        // if contains the keyword, we generate a new vcsUrl key and revision hash
                        result.buildInfo.vcsRevision = vcs.revision
                        result.buildInfo.vcsUrl = vcs.url
                    }
                }
            }
            return result['buildInfo']
        } else {
            throw new ArtifactException("Artifactory API error: result doesn't include buildInfo")
        }
    }

    /**
     * Get build information from Artifactory.
     *
     * @see #getBuildInfo(Map)
     */
    Map getBuildInfo(String buildName, String buildNumber, String vcsUrlKeyword) {
        return getBuildInfo([
            'build-name'      : buildName,
            'build-number'    : buildNumber,
            'vcs-url-keyword' : vcsUrlKeyword
        ])
    }

    /**
     * Download artifacts
     *
     * @Note This method is implemented with jFrog CLI. The reason is sortBy in download spec is not
     *       supported by Jenkins Artifactory plugin.
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @Example Download 5 artifacts to local:
     * <pre>
     *     jfrog.download(
     *         spec        : 'path/to/my/download/spec.json',
     *         expected    : 5
     *     )
     * </pre>
     * <p>Or specify download spec in parameter</p>
     * <pre>
     *     jfrog.download(
     *         specContent : '[{"file": "lib-snapshot-local/path/to/file.zip"}]',
     *         expected    : 1
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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
        if (args.containsKey('specContent')) {
            this.steps.writeFile encoding: 'UTF-8', file: tmpFile, text: args['specContent']
        }
        Integer expectedArtifacts = args.containsKey('expected') ? (args['expected'] as Integer) : -1

        // download
        this.steps.sh "echo 'spec:' && cat ${specFile}"
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
        }

        if (expectedArtifacts > 0) {
            if ((downloadResultObject['totals']['success'] as Integer) != expectedArtifacts) {
                throw new ArtifactException("Expected ${expectedArtifacts} artifact(s) to be downloaded but only got ${downloadResultObject['totals']['success']}.")
            }
        }

        // remove spec file if it's temporary
        if (this.steps.fileExists(tmpFile)) {
            this.steps.sh("rm ${tmpFile}")
        }

        this.steps.echo "Artifact downloading is successful."
    }

    /**
     * Upload an artifact.
     *
     * <p><strong>Requires these environment variables:</strong><ul>
     * <li>- JOB_NAME</li>
     * <li>- BUILD_NUMBER</li>
     * </ul></p>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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
     * <p>You can choose one of three ways to upload:<ul>
     * <li>- specify {@code spec} which pointing to a file</li>
     * <li>- specify {@code specContent} which is upload specification text</li>
     * <li>- specify {@code pattern} and {@code target}, these information will be rendered into upload specification</li>
     * </ul></p>
     *
     * @Example Upload {@code my-project.zip} to artifactory {@coode lib-snapshot-local} repository.
     * <pre>
     *     jfrog.upload(
     *         pattern : 'build/result/of/my-project.zip',
     *         target  : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
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
                // convert params to required format
                // NOTE: because of the way of passing properties, all property values
                //       are converted to string
                extraProperties = args['properties'].collect {
                    k, v -> k + '=' + URLEncoder.encode("${v}", 'UTF-8')
                }.join(';')
            }
            spec = "{\n" +
                   "  \"files\": [\n" +
                   "    {\n" +
                   "      \"pattern\": \"${args['pattern']}\",\n" +
                   "      \"target\": \"${args['target']}\",\n" +
                   "      \"props\": \"${extraProperties}\"\n" +
                   "    }\n" +
                   " ]\n" +
                   "}"
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
     * Upload an artifact.
     *
     * @see #upload(Map)
     */
    void upload(String pattern, String target, Map properties = [:]) {
        this.upload(pattern: pattern, target: target, properties: properties)
    }

    /**
     * Delete an artifact
     *
     * @Note This method is implemented with jFrog CLI.
     *
     * @Example Delete {@code my-project.zip} from artifactory {@coode lib-snapshot-local} repository.
     * <pre>
     *     jfrog.delete(
     *         pattern : 'build/result/of/my-project.zip'
     *     )
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  pattern          pattern to locate the artifacts
     * @return                  number of artifacts successfully or failed to delete
     */
    Map delete(Map args = [:]) throws InvalidArgumentException, ArtifactException {
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
        if (!args['pattern']) {
            throw new InvalidArgumentException('pattern')
        }

        this.steps.echo "Deleting artifact \"${args['pattern']}\" ..."

        def resultText = this.steps.sh(
            script: "jfrog rt delete --quiet \"${args['pattern']}\"",
            returnStdout: true
        ).trim()
        // this.steps.echo "Raw rt delete result:\n${resultText}"
        /*
        Example result:
        {
            "status": "success",
            "totals": {
                "success": 1,
                "failure": 0
            }
        }
        */
        def resultJson = this.steps.readJSON text: resultText
        if (!resultJson || !resultJson['status'] || resultJson['status'] != 'success') {
            throw new ArtifactException("Failed to delete artifact(s): ${resultJson}")
        }
        this.steps.echo "Artifact deletion result:\n" +
            "- status  : ${resultJson['status']}\n" +
            "- success : ${resultJson['totals']['success']}\n" +
            "- failure : ${resultJson['totals']['failure']}"

        Map result = [
            'success': resultJson['totals']['success'],
            'failure': resultJson['totals']['failure']
        ]

        return result
    }

    /**
     * Delete an artifact
     *
     * @see #upload(Map)
     */
    Map delete(String pattern) {
        return this.delete(pattern: pattern)
    }

    /**
     * Promote artifact.
     *
     * <p><strong>Requires these environment variables:</strong><ul>
     * <li>- JOB_NAME</li>
     * <li>- BUILD_NUMBER</li>
     * </ul></p>
     *
     * @Example Promote {@code my-project-0.1.2-snapshot.zip} as formal release of v0.1.2:
     * <pre>
     *     String result = jfrog.promote(
     *         source     : 'lib-snapshot-local/org/zowe/my-project/0.1.2/my-project-0.1.2-snapshot.zip',
     *         targetPath : 'lib-release-local/org/zowe/my-project/0.1.2/',
     *         targetName : 'my-project-0.1.2.zip'
     *     )
     *     assert result == 'lib-release-local/org/zowe/my-project/0.1.2/my-project-0.1.2.zip'
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  source            information of the artifact will be promoted.
     * @param  targetPath        target path on remote Artifactory
     * @param  targetName        target artifact name. Optional, default to original name.
     * @return                   full path to the promoted artifact
     */
    String promote(Map args = [:]) throws InvalidArgumentException, ArtifactException {
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
        if (!args.containsKey('source')) {
            throw new InvalidArgumentException('source')
        }
        if (!args.containsKey('targetPath')) {
            throw new InvalidArgumentException('targetPath')
        }

        // get soruce artifact information if not present
        Map source
        if (args['source'] instanceof Map) {
            source = args['source']
        } else if (args['source'] instanceof String) {
            source = this.getArtifact(args['source'])
        } else {
            throw new InvalidArgumentException('source')
        }

        if (!source.containsKey('path')) {
            throw new InvalidArgumentException('source', 'path property is missing from source artifact information.')
        }

        // sanitize targetPath
        def targetPath = args['targetPath']
        if (targetPath.endsWith('/')) {
            targetPath = targetPath.substring(0, targetPath.length() - 1)
        }

        // extract file name if not provided
        def targetName
        if (args.containsKey('targetName') && args['targetName']) {
            targetName = args['targetName']
        } else {
            def sourceFilenameTrunks = source['path'].split('/')
            if (sourceFilenameTrunks.size() < 1) {
                throw new ArtifactException("Invalid artifact: ${source['path']}")
            }
            targetName = sourceFilenameTrunks[-1]
        }

        def env = this.steps.env
        def buildTimestamp = source.containsKey('build.timestamp') ? source['build.timestamp'] : ''
        def buildName      = source.containsKey('build.name') ? source['build.name'] : ''
        def buildNumber    = source.containsKey('build.number') ? source['build.number'] : ''

        def targetFullPath = "${targetPath}/${targetName}"

        // variables prepared, ready to promote
        this.steps.echo "Promoting artifact: ${source['path']}\n" +
                        "- to              : ${targetFullPath}\n" +
                        "- build name      : ${buildName}\n" +
                        "- build number    : ${buildNumber}\n" +
                        "- build timestamp : ${buildTimestamp}\n"

        // promote (copy) artifact
        def promoteResult = this.steps.sh(
            script: "jfrog rt copy --flat \"${source.path}\" \"${targetFullPath}\"",
            returnStdout: true
        ).trim()

        // validate result
        def promoteResultObject = this.steps.readJSON(text: promoteResult)
        this.steps.echo "Artifact promoting result:\n" +
            "- status  : ${promoteResultObject['status']}\n" +
            "- success : ${promoteResultObject['totals']['success']}\n" +
            "- failure : ${promoteResultObject['totals']['failure']}"
        if (promoteResultObject['status'] != 'success' ||
            promoteResultObject['totals']['success'] != 1 || promoteResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact is not promoted successfully.")
        }

        // prepare artifact property
        def props = []
        def currentBuildName = env.JOB_NAME.replace('/', ' :: ')
        props << "build.name=${currentBuildName}"
        props << "build.number=${env.BUILD_NUMBER}"
        if (buildName) {
            props << "build.parentName=${buildName}"
        }
        if (buildNumber) {
            props << "build.parentNumber=${buildNumber}"
        }
        if (buildTimestamp) {
            props << "build.timestamp=${buildTimestamp}"
        }

        // update artifact property
        this.steps.echo "Updating artifact properties:\n${props.join("\n")}"

        def setPropsResult = this.steps.sh(
            script: "jfrog rt set-props \"${targetFullPath}\" \"" + props.join(';') + "\"",
            returnStdout: true
        ).trim()
        def setPropsResultObject = this.steps.readJSON(text: setPropsResult)
        this.steps.echo "Artifact promoting result:\n" +
            "- status  : ${setPropsResultObject['status']}\n" +
            "- success : ${setPropsResultObject['totals']['success']}\n" +
            "- failure : ${setPropsResultObject['totals']['failure']}"
        if (setPropsResultObject['status'] != 'success' ||
            setPropsResultObject['totals']['success'] != 1 || setPropsResultObject['totals']['failure'] != 0) {
            throw new ArtifactException("Artifact property is not updated successfully.")
        }

        return targetFullPath
    }

    /**
     * Promote artifact.
     *
     * @see #promote(Map)
     */
    String promote(String source, String targetPath, String targetName = '') {
        return this.promote([source: source, targetPath: targetPath, targetName: targetName])
    }

    /**
     * Interpret artifact definition
     *
     * @Example Consider these scenarios:
     * <pre>
     * "org.zowe.zlux.zlux-core": {
     *     &#47;&#47; no * in the pattern, will pick exact artifact with path
     *     "artifact": "libs-snapshot-local&#47;org&#47;zowe&#47;zlux&#47;zlux-core&#47;1.3.0-RC&#47;zlux-core-1.3.0-20190607.143930.pax"
     * },
     * "org.zowe.explorer-jes": {
     *     &#47;&#47; still pick exact artifact by release version
     *     &#47;&#47; match 0.0.21 in libs-release-local&#47;org&#47;zowe&#47;explorer-jes&#47;0.0.21&#47;*
     *     &#47;&#47; and it should only have one artifact in the folder
     *     "version": "0.0.21"
     * },
     * "org.zowe.explorer-mvs": {
     *     &#47;&#47; pick the most recent patch-level build
     *     &#47;&#47; match 0.0.* from libs-snapshot-local&#47;org&#47;zowe&#47;explorer-mvs&#47;0.0.*&#47;*
     *     "version": "~0.0.21",
     *     &#47;&#47; optional to specify which artifact to pick from, if more than one in the folder
     *     &#47;&#47; with this option, will try to find the artifact with pattern libs-snapshot-local&#47;org&#47;zowe&#47;explorer-mvs&#47;0.0.*&#47;explorer-mvs-special-name-*.pax
     *     "artifact": "explorer-mvs-special-name-*.pax"
     * },
     * "org.zowe.another": {
     *     &#47;&#47; pick the most recent minor-level build
     *     &#47;&#47; match 0.* from libs-snapshot-local&#47;org&#47;zowe&#47;another&#47;0.*&#47;*
     *     "version": "^0.0.21",
     * },
     * "license": {
     *     &#47;&#47; pick exact artifact defined by artifact
     *     "artifact": "libs-release-local&#47;org&#47;zowe&#47;licenses&#47;1.0.0&#47;zowe_licenses_full.zip"
     * },
     * "org.zowe.licenses": {
     *     &#47;&#47; another way to define with same effect above
     *     "version": "1.0.0"
     * }
     * </pre>
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param  packageName       package name. For example {@code org.zowe.explorer-jes}
     * @param  definition        map of definition. Supported keys are: version, artifact, explode, target, repository
     * @param  defaults          default values for definition. Supported keys are: target, repository
     * @return                   map of Artifactory download spec
     */
    Map interpretArtifactDefinition(String packageName, Map definition, Map defaults = [:]) throws InvalidArgumentException {
        Map result = [:]
        String packagePath = packageName.replaceAll(/\./, '/')
        String repository = ''
        log.fine("args.packageName = ${packageName}")
        log.fine("args.definition  = ${definition}")
        log.fine("args.defaults    = ${defaults}")

        // target & explode will be copied over
        if (definition.containsKey('target')) {
            result['target'] = definition['target'] as String
        } else if (defaults.containsKey('target')) {
            result['target'] = defaults['target'] as String
        }
        if (definition.containsKey('repository')) {
            repository = definition['repository'] as String
        } else if (defaults.containsKey('repository')) {
            repository = defaults['repository'] as String
        }
        if (definition.containsKey('explode')) {
            result['explode'] = definition['explode'] as String
        }
        if (definition.containsKey('excludePatterns')) {
            result['excludePatterns'] = definition['excludePatterns']
        }
        // jfrog cli shows warning of deprecation:
        // [Warn] The --exclude-patterns command option and the 'excludePatterns' File Spec property are deprecated.
        //         Please use the --exclusions command option or the 'exclusions' File Spec property instead.
        //         Unlike exclude-patterns, exclusions take into account the repository as part of the pattern.
        //         For example:
        //         "excludePatterns": ["a.zip"]
        //         can be translated to
        //         "exclusions": ["repo-name/a.zip"]
        //         or
        //         "exclusions": ["*/a.zip"]
        if (definition.containsKey('exclusions')) {
            result['exclusions'] = definition['exclusions']
        }
        // always flat
        result['flat'] = 'true'
        result['pattern'] = ''

        if (definition.containsKey('artifact')) {
            if (definition['artifact'].contains('/')) {
                // assume this is a full path to the artifact
                result['pattern'] = definition['artifact'] as String
            }
        }

        log.finer("result (before parsing version) = ${result}")

        if (!result['pattern'] && definition.containsKey('version')) {
            def m1 = (definition['version'] =~ /^~([0-9]+)\.([0-9]+)\.([0-9]+)(-.+)?$/)
            def m2 = definition['version'] =~ /^\^([0-9]+)\.([0-9]+)\.([0-9]+)(-.+)?$/
            if (m1.matches()) {
                if (!repository) {
                    repository = m1[0][4] ? REPOSITORY_SNAPSHOT : REPOSITORY_RELEASE
                }
                result['pattern'] = repository + '/' + packagePath + '/' +
                                    m1[0][1] + '.' + m1[0][2] + ".*${m1[0][4] ?: ''}/"
            } else if (m2.matches()) {
                if (!repository) {
                    repository = m2[0][4] ? REPOSITORY_SNAPSHOT : REPOSITORY_RELEASE
                }
                result['pattern'] = repository + '/' + packagePath + '/' +
                                    m2[0][1] + ".*${m2[0][4] ?: ''}/"
            } else {
                // parse semantic version, this may throw exception if version is invalid
                Map sv = Utils.parseSemanticVersion(definition['version'])
                if (sv['prerelease'] == '' && sv['metadata'] == '') {
                    // this is formal release
                    result['pattern'] = (repository ?: REPOSITORY_RELEASE) + '/' + packagePath + '/' + definition['version'] + '/'
                } else if (sv['prerelease'] != '') {
                    result['pattern'] = (repository ?: REPOSITORY_SNAPSHOT) + '/' + packagePath + '/' +
                        sv['major'] + '.' + sv['minor'] + '.' + sv['patch'] + '-' + sv['prerelease'] + '/'
                }
            }

            if (result['pattern']) {
                if (definition.containsKey('artifact')) {
                    result['pattern'] += definition['artifact']
                } else {
                    result['pattern'] += '*'
                }
            }
        }

        if (!result['pattern']) {
            throw new InvalidArgumentException('definition', 'Invalid artifact definition, either artifact nor version is provided.')
        }

        if (result['pattern'] =~ /\*.*\/[^\/]+$/) {
            // if we have * in the path, we only pick the most recent artifact
            // if we only have * in the artifact name, we may want to pick more than one
            result["sortBy"] = ["created"]
            result["sortOrder"] = "desc"
            result["limit"] = 1
        }

        log.fine("result (final) = ${result}")

        return result
    }

    /**
     * Convert Map of package name - definition to Artifactory download specification
     *
     * @param  definitions       Map of package name - definition pairs
     * @param  defaults          default values for definition. Supported keys are: target, repository
     * @return                   map of download spec with 'files' List
     */
    Map interpretArtifactDefinitions(Map definitions, Map defaults = [:]) {
        def result
        if (this.steps) {
            result = this.steps.readJSON text: '{"files":[]}'
        } else {
            result = ['files': []]
        }

        definitions.each { packageName, definition ->
            result['files'].push(this.interpretArtifactDefinition(packageName, definition, defaults))
        }

        return result
    }
}
