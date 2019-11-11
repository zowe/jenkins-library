/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.integrationtest

import groovy.json.JsonSlurper
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import org.awaitility.Awaitility
import org.awaitility.Duration
import static org.hamcrest.Matchers.*
import org.zowe.jenkins_shared_library.scm.GitHub
import org.zowe.jenkins_shared_library.Utils

/**
 * Class to handle API request/response with Jenkins server
 */
class GitHubAPI {
    /**
     * Singleton instance
     */
    static GitHubAPI instance

    /**
     * Github repository name
     */
    String repository

    /**
     * logger object to write logs
     */
    transient Logger logger

    /**
     * Construct
     *
     * @param  repository            GitHub repository in a format of "owner/repo"
     */
    GitHubAPI(String repo) {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())

        // validate parameters
        this.repository = repo
    }

    /**
     * Send GET http request to Jenkins server
     *
     * @param  url         http url
     * @param  fetchNext   if continue to fetch next url if the response indicates more results
     * @return             map of response with keys: code, headers, body
     */
    Map get(String url, Boolean fetchNext = false) throws IOException {
        logger.finer("GET ${url}")

        def conn = new URL("${url}").openConnection()
        conn.setInstanceFollowRedirects(false);

        def result = [:]
        try {
            result['code'] = conn.getResponseCode()
            result['headers'] = conn.getHeaderFields()
            result['body'] = conn.getInputStream().getText()
        } catch (e) {
            throw e
        } finally {
            // log result
            logger.finest("< Response: ${result.inspect()}")
        }

        result = _transformResult(result)

        if (fetchNext && result['headers'].containsKey('Link')) {
            String nextUrl
            result['headers']['Link'][0].split(/, /).each {
                def matcher = it =~ /<([^>]+)>;\s+rel="next"/
                if (matcher.find()) {
                    nextUrl = matcher[0][1]
                }
            }

            if (nextUrl) {
                Map nextResult = this.get(nextUrl, fetchNext)
                // body should be a collection!
                result['body'] = result['body'] + nextResult['body']
            }
        }

        return result
    }

    /**
     * Transform result body if:
     *
     * - content is json
     *
     * @param  result response map of with keys: code, headers, body
     * @return        transformed response map
     */
    Map _transformResult(Map result) {
        if (!result['body'] || !result['headers']) {
            return result
        }

        boolean isJson = false

        for (header in result['headers']) {
            if (header.key && header.key.equalsIgnoreCase('Content-Type')) {
                for (ct in header.value) {
                    if (ct.substring(0, 16).equalsIgnoreCase('application/json')) {
                        isJson = true
                        break
                    }
                }
            }
            if (isJson) {
                break
            }
        }

        if (isJson) {
            logger.finest('Response body content is JSON, will be transformed.')
            result['body'] = new JsonSlurper().parseText(result['body'])
        }

        return result
    }

    /**
     * Load content of file
     * @param  branch      which branch
     * @param  file        path to the file
     * @return             content of file
     */
    def readFile(String branch, String file) {
        if (!branch) {
            throw new GitHubAPIException('Branch name is required to read file from github.')
        }

        String url = "https://${GitHub.GITHUB_API_DOMAIN}/repos/${this.repository}/contents/${file}"

        Map result = this.get(url)
        String encodedContent = result['body']['content']
        // content is base64 encoded
        String content = new String(encodedContent.decodeBase64())
        logger.finer("${file} content:\n${content}")

        return content
    }

    /**
     * Load version information from package.json
     * @param  branch               which branch
     * @param  packageJsonFile      path to package.json
     * @return                      version string
     */
    def getVersionFromPackageJson(String branch, String packageJsonFile = 'package.json') {
        String content = this.readFile(branch, packageJsonFile)
        // package.json should be a json content
        def contentJson = (new JsonSlurper()).parseText(content)
        if (!contentJson || !contentJson['version']) {
            throw new Exception("Failed to find version from ${packageJsonFile}")
        }
        def version = Utils.parseSemanticVersion(contentJson['version'])

        return version
    }

    /**
     * Load version information from Dockerfile
     * @param  branch               which branch
     * @param  dockerFile           path to Dockerfile
     * @return                      version string
     */
    def getVersionFromDockerfile(String branch, String dockerFile = 'Dockerfile') {
        String content = this.readFile(branch, dockerFile)
        // Dockerfile should have "LABEL version=?"
        def matcher = content =~ /[Ll][Aa][Bb][Ee][Ll]\s+version=([0-9."]+)/
        def versionInDockerfile
        if (matcher.find()) {
            versionInDockerfile = matcher[0][1].replaceAll(/"/, "")
        }
        if (!versionInDockerfile) {
            throw new Exception("Failed to find version from ${dockerFile}")
        }
        def version = Utils.parseSemanticVersion(versionInDockerfile)

        return version
    }

    /**
     * Load version information from gradle.properties
     * @param  branch               which branch
     * @param  gradlePropertiesFile      path to gradle.properties
     * @return                      content of gradle.properties as a Map
     */
    def getVersionFromGradleProperties(String branch, String gradlePropertiesFile = 'gradle.properties') {
        String content = this.readFile(branch, gradlePropertiesFile)

        String version = ''
        content.split("\n").each{ line ->
            def matches = line =~ /^\s*version\s*=(.+)$/
            if (matches.matches() && matches[0] && matches[0].size() == 2) {
                version = matches[0][1].trim()
            }
        }
        if (!version) {
            throw new Exception("Failed to find version from ${gradlePropertiesFile}")
        }

        return Utils.parseSemanticVersion(version)
    }

    /**
     * Get tag list
     * @return        list of tag names
     */
    List<String> getTags() {
        String tagsUrl = "https://${GitHub.GITHUB_API_DOMAIN}/repos/${this.repository}/tags"
        Map result = this.get(tagsUrl, true)

        List<String> tagNames = []
        result.body.each {
            tagNames.push(it['name'])
        }

        return tagNames
    }
}
