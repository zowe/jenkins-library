/**
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
     * @param  url     http url
     * @return          map of response with keys: code, headers, body
     */
    Map get(String url) throws IOException {
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
     * Load content of package.json
     * @param  branch      which branch
     * @return             content of package.json as a Map
     */
    def readPackageJson(String branch, String packageJsonFile = 'package.json') {
        if (!branch) {
            throw new GitHubAPIException('Branch name is required to read package.json.')
        }

        String packageJsonUrl = "https://${GitHub.GITHUB_API_DOMAIN}/repos/${this.repository}/contents/package.json"

        Map result = this.get(packageJsonUrl)
        String encodedContent = result['body']['content']
        // content is base64 encoded
        String content = new String(encodedContent.decodeBase64())
        logger.finer("package.json content: ${content}")
        // package.json should be a json content
        def contentJson = (new JsonSlurper()).parseText(content)

        return contentJson
    }

    /**
     * Get tag list
     * @return        list of tag names
     */
    List<String> getTags() {
        String tagsUrl = "https://${GitHub.GITHUB_API_DOMAIN}/repos/${this.repository}/tags"
        Map result = this.get(tagsUrl)

        List<String> tagNames = []
        result.body.each {
            tagNames.push(it['name'])
        }

        return tagNames
    }
}
