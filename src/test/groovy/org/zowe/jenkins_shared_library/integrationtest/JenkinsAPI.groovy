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
import org.zowe.jenkins_shared_library.Utils

/**
 * Class to handle API request/response with Jenkins server
 */
class JenkinsAPI {
    /**
     * Singleton instance
     */
    static JenkinsAPI instance

    /**
     * URL to retrieve crumb
     */
    private final CRUMB_URL = '/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)'

    /**
     * Jenkins server base URI
     */
    String jenkinsBaseUri
    /**
     * Jenkins Authorization header string
     */
    String jenkinsAuthorization
    /**
     * If enable Jenkins crumb header
     */
    boolean jenkinsCrumbEnabled = false
    /**
     * Jenkins crumb ID which will be used in Jenkins-Crumb header
     */
    String jenkinsCrumb

    /**
     * logger object to write logs
     */
    transient Logger logger

    /**
     * Construct
     *
     * @param  baseUri               Jenkins server URI
     * @param  user                  user name to login to Jenkins
     * @param  password              password or token of the Jenkins user
     * @param  options               options to control how to get JenkinsAPI instance, supports:
     *                               - crumb:   if enable/disable Jenkins crumb header
     * @throws JenkinsAPIException   if failed on validation, for example, missing Jenkins URI
     */
    JenkinsAPI(String baseUri, String user = '', String password = '', Map options = [:]) throws JenkinsAPIException {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())

        // init Awaitility
        Awaitility.setDefaultTimeout(1, TimeUnit.MINUTES)
        Awaitility.setDefaultPollInterval(Duration.FIVE_SECONDS)
        Awaitility.setDefaultPollDelay(1, TimeUnit.SECONDS)

        // validate parameters
        jenkinsBaseUri = baseUri
        if (user && password) {
            String userpass = "${user}:${password}"
            jenkinsAuthorization = 'Basic ' + new String(Base64.getEncoder().encode(userpass.getBytes()))
        } else {
            throw new JenkinsAPIException('Jenkins username and password/token is required')
        }

        if (options.containsKey('crumb') && options['crumb']) {
            jenkinsCrumbEnabled = true
        }

        logger.config("Jenkins server initialized: ${jenkinsBaseUri}")

        // make sure test folder exists
        ensureFolder(Constants.INTEGRATION_TEST_JENKINS_FOLDER)
    }

    /**
     * Initialize a JenkinsAPI singleton
     *
     * @return                         a JenkinsAPI object
     * @throws JenkinsAPIException     if failed on validation, for example, missing Jenkins URI
     */
    static init() throws JenkinsAPIException {
        String uri = System.getProperty('jenkins.baseuri')
        if (!uri) {
            throw new JenkinsAPIException('Failed to read property jenkins.baseuri')
        }
        if (uri[-1..-1] == '/') { // remove trailing slash
            uri = uri[0..-2]
        }
        String user = System.getProperty('jenkins.user')
        String password = System.getProperty('jenkins.password')
        String crumb = System.getProperty('jenkins.crumb')
        if (!instance) {
            instance = new JenkinsAPI(uri, user, password, [
                'crumb': crumb == 'true'
            ])
        }

        return instance
    }

    /**
     * Load job template file from resource jobTemplates folder
     *
     * @param  template     template name
     * @return              template content
     */
    static String loadJobTemplate(String template) throws IOException {
        return Utils.loadResource("src/test/resources/jobTemplates/${template}")
    }

    /**
     * Render URL for a job
     *
     * @param  paths      paths to the job, for example: ['my-folder', 'my-job', 'master']
     * @param  action     action URL
     * @param  params     URL parameters
     * @return            full url to the job action
     */
    String getJobUrl(List<String> paths, String action = '/', Map params = [:]) {
        String url = ''

        for (path in paths) {
            url += '/job/' + URLEncoder.encode(path, 'UTF-8')
        }
        if (action && action != '/') {
            url += action
        }
        // convert params to querystring
        if (params.size() > 0) {
            url += '?' + Utils.getUriQueryString(params)
        }

        return url
    }

    /**
     * Send GET http request to Jenkins server
     *
     * @param  path     http path
     * @return          map of response with keys: code, headers, body
     */
    Map get(String path) throws IOException {
        logger.finer("GET ${jenkinsBaseUri}${path}")

        def conn = new URL("${jenkinsBaseUri}${path}").openConnection()
        conn.setInstanceFollowRedirects(false);
        if (jenkinsAuthorization) {
            conn.setRequestProperty('Authorization', jenkinsAuthorization)
        }
        // do we need crumb for GET?
        // if (jenkinsCrumbEnabled && path != CRUMB_URL) {
        //     def crumb = _requestCrumb()
        //     conn.setRequestProperty('Jenkins-Crumb', crumb)
        // }

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
     * Send POST http request to Jenkins server
     *
     * @param  path     http path
     * @param  body     http POST body
     * @param  headers  http headers
     * @return          map of response with keys: code, headers, body
     */
    Map post(String path, String body = '', Map headers = [:]) throws IOException {
        logger.finer("POST ${jenkinsBaseUri}${path}")

        def conn = new URL("${jenkinsBaseUri}${path}").openConnection()
        conn.setRequestMethod('POST')
        conn.setDoOutput(true)
        conn.setInstanceFollowRedirects(false);
        for (header in headers) {
            conn.setRequestProperty(header.key, header.value)
        }
        if (jenkinsAuthorization) {
            conn.setRequestProperty('Authorization', jenkinsAuthorization)
        }
        if (jenkinsCrumbEnabled) {
            def crumb = _requestCrumb()
            conn.setRequestProperty('Jenkins-Crumb', crumb)
        }
        conn.getOutputStream().write(body.getBytes('UTF-8'))

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
     * Request Jenkins crumb if the server requires crumb header. The crumb header
     * is used if Jenkins has CSRF protection
     *
     * This could be disabled by project property: jenkins.crumb
     *
     * @return              the crumb string
     * @throws JenkinsAPIException if failed to get crumb from Jenkins server
     */
    String _requestCrumb() throws JenkinsAPIException {
        if (!jenkinsCrumb) {
            logger.fine("Requesting Jenkins Crumb header ...")

            Map result = get(CRUMB_URL)
            // Jenkins-Crumb:25a3f51eb877771cc558e2f7911de531
            if (result['body'] && result['body'].substring(0, 14).equalsIgnoreCase('jenkins-crumb:')) {
                jenkinsCrumb = result['body'].substring(15).trim()
            }

            if (!jenkinsCrumb) {
                throw new JenkinsAPIException("Failed to find Jenkins crumb from response ${result['body']}")
            }

            logger.finest("Jenkins crumb retrieved: ${jenkinsCrumb}")
        }

        return jenkinsCrumb
    }

    /**
     * Make sure a Jenkins folder exists, if not, create the folder
     *
     * @param  folder              folder name which should exist
     * @param  parentFolder        parent folder where we do the check
     */
    void ensureFolder(String folder, List<String> parentFolder = []) {
        def result = get(getJobUrl(parentFolder, '/api/json'))

        boolean foundFolder = false
        if (result['body'] && result['body']['jobs']) {
            for (job in result['body']['jobs']) {
                // _class:com.cloudbees.hudson.plugins.folder.Folder, name:test-folder1
                if (job['_class'] == 'com.cloudbees.hudson.plugins.folder.Folder' &&
                    job['name'] == folder) {
                    foundFolder = true
                    break
                }
            }
        }

        if (!foundFolder) {
            logger.fine("Folder \"${folder}\" doesn't exist, should be created")
            createJob(folder, 'folder.xml')
        } else {
            logger.fine("Folder \"${folder}\" exists already")
        }
    }

    /**
     * Create a Jenkins job with job template
     *
     * @param  name                job name to create
     * @param  template            job template xml file name
     * @param  parentFolder        in which folder the job should be created
     * @param  macros              macros in key/value pair should be replaced before sending the request
     * @return                     true if the job is created successfully
     * @throws JenkinsAPIException if it failed to create the job
     */
    boolean createJob(String name, String template, List<String> parentFolder = [], Map macros = [:]) throws JenkinsAPIException {
        logger.fine("Creating job ${name} in ${parentFolder} with template ${template} ...")

        String xml = loadJobTemplate(template)
        macros.each {
            k, v -> xml = xml.replace('{' + k + '}', v)
        }
        Map result = post(getJobUrl(parentFolder, '/createItem', ['name': name]), xml, [
            'Content-Type': 'application/xml'
        ])

        if (result['code'] != 200) {
            throw new JenkinsAPIException("Failed to create job ${name}: ${result['body']}")
        }

        // start repository scan for multibranch pipeline
        if (xml.contains('</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>')) {
            logger.fine("Starting repository scanning for multibranch pipeline job ...")

            parentFolder.add(name)
            Map repoScanResult = post(getJobUrl(parentFolder, '/build', ['delay': '0']))
            if (repoScanResult['code'] != 302) {
                throw new JenkinsAPIException("Failed to trigger job ${name} to scan repository: ${result['body']}")
            }

            // wait until repository scan finished
            Awaitility.await('repository scanning')
                .until(getChildrenJobsCallable(parentFolder), hasItem(equalTo('master')))
        }

        return true
    }

    /**
     * List children jobs for a job
     *
     * @param  name   paths to find the parent job. For example: ['in-my-folder', 'parent-job']
     * @return        a list of children jobs
     */
    List<String> getChildrenJobs(List<String> name) {
        logger.finer("Listing children of job ${name} ...")

        Map result = get(getJobUrl(name, '/api/json'))

        List jobs = []
        if (result['body'] && result['body']['jobs']) {
            for (job in result['body']['jobs']) {
                jobs.add(job['name'])
            }
        }

        logger.finer("Children jobs of ${name}: ${jobs}")

        return jobs;
    }

    /**
     * Callable version of getChildrenJobs() method
     *
     * @param  name   paths to find the parent job. For example: ['in-my-folder', 'parent-job']
     * @return        a Callable which wraps getChildrenJobs() method
     */
    Callable<List> getChildrenJobsCallable(List<String> name) {
        return new Callable<List>() {
            public List call() {
                return getChildrenJobs(name)
            }
        };
    }

    /**
     * Delete a job from Jenkins
     *
     * @param  name                job name to delete
     * @param  folder              which folder the job is located
     * @return                     true if the job is deleted successfully
     * @throws JenkinsAPIException if it failed to delete the job
     */
    boolean deleteJob(List<String> name) throws JenkinsAPIException {
        logger.fine("Deleting job ${name} ...")

        Map result = post(getJobUrl(name, '/doDelete'))

        if (result['code'] != 302) {
            throw new JenkinsAPIException("Failed to delete job ${name}: ${result['body']}")
        }

        return true
    }

    /**
     * Start building a job
     * @param  name    full list of paths to the job
     * @param  params  parameters to start the job
     * @return         true if the job is started
     */
    boolean startJob(List<String> name, Map params = [:]) throws JenkinsAPIException {
        logger.fine("Starting job ${name} with parameters ${params} ...")

        Map result = post(
            getJobUrl(name, params.size() > 0 ? '/buildWithParameters' : '/build'),
            Utils.getUriQueryString(params),
            [
                'Content-Type': 'application/x-www-form-urlencoded'
            ]
        )

        if (result['code'] != 201) {
            throw new JenkinsAPIException("Failed to delete job ${name}: ${result['body']}")
        }

        return true
    }

    /**
     * Find the last build number of a job
     *
     * @param  name   paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @return        last build number. -1 if not found
     */
    Integer getLastBuildNumber(List<String> name) {
        logger.finer("Getting last build number of job ${name} ...")

        Map result = get(getJobUrl(name, '/api/json'))

        if (result['body'] && result['body']['lastBuild'] && result['body']['lastBuild']['number']) {
            return result['body']['lastBuild']['number']
        } else {
            return -1
        }
    }

    /**
     * Callable version of getLastBuildNumber() method
     *
     * @param  name   paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @return        a Callable which wraps getLastBuildNumber() method
     */
    Callable<Integer> getLastBuildNumberCallable(List<String> name) {
        return new Callable<Integer>() {
            public Integer call() {
                return getLastBuildNumber(name)
            }
        };
    }

    /**
     * Check if a build is finished
     *
     * @param  name         paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @param  buildNumber  build number
     * @return              true if it's finished
     */
    Boolean isBuildFinished(List<String> name, Integer buildNumber) {
        logger.finer("Checking if build ${name}#${buildNumber} is finished or not ...")

        Map result = get(getJobUrl(name, "/${buildNumber}/api/json"))

        if (result['body'] && result['body']['building']) {
            return false
        } else {
            return true
        }
    }

    /**
     * Callable version of isBuildFinished() method
     *
     * @param  name         paths to find the parent job. For example: ['in-my-folder', 'parent-job']
     * @param  buildNumber  build number
     * @return              a Callable which wraps isBuildFinished() method
     */
    Callable<Boolean> isBuildFinishedCallable(List<String> name, Integer buildNumber) {
        return new Callable<Boolean>() {
            public Boolean call() {
                return isBuildFinished(name, buildNumber)
            }
        };
    }

    /**
     * Get build information
     *
     * @param  name         paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @param  buildNumber  build number
     * @return              map of build information includes: number, result, timestamp, etc
     */
    Map getBuildInformation(List<String> name, Integer buildNumber) {
        logger.finer("Checking build ${name}#${buildNumber} information ...")

        Map result = get(getJobUrl(name, "/${buildNumber}/api/json"))

        logger.finest("Build ${name}#${buildNumber} information is:\n${result['body']}")
        return result['body']
    }

    /**
     * Get build result
     *
     * @param  name         paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @param  buildNumber  build number
     * @return              SUCCESS, FAILURE, UNSTABLE etc
     */
    String getBuildResult(List<String> name, Integer buildNumber) throws JenkinsAPIException {
        logger.finer("Checking build ${name}#${buildNumber} result ...")

        Map info = getBuildInformation(name, buildNumber)

        if (info && info['result']) {
            logger.finer("Build ${name}#${buildNumber} result is ${info['result']}")
            return info['result']
        } else {
            throw new JenkinsAPIException("Failed to find build result of ${name}#{$buildNumber}: ${result['body']}")
        }
    }

    /**
     * Get build console log
     *
     * @param  name         paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @param  buildNumber  build number
     * @return              SUCCESS, FAILURE, UNSTABLE etc
     */
    String getBuildLog(List<String> name, Integer buildNumber) throws JenkinsAPIException {
        logger.finer("Checking build ${name}#${buildNumber} console log ...")

        Map result = get(getJobUrl(name, "/${buildNumber}/consoleText"))

        if (result['body']) {
            logger.finest("Build ${name}#${buildNumber} console log is:\n${result['body']}")
            return result['body']
        } else {
            throw new JenkinsAPIException("Failed to find build console log of ${name}#{$buildNumber}: ${result['body']}")
        }
    }

    /**
     * Start a job and wait until it's finished
     *
     * @param  name   paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     * @return              build information map
     */
    Map startJobAndGetBuildInformation(List<String> name, Map params = [:]) {
        // get old last build number
        Integer oldLastBuildNumber = getLastBuildNumber(name)

        // start the job
        startJob name, params

        // FIXME: how to find the last build ID?
        // wait for a while for lastBuildNumber be refreshed with new started build
        sleep(2000)

        // wait until we find the last build ID
        Awaitility.await("waiting for last build information pops")
            .atMost(5, TimeUnit.MINUTES)
            .until(getLastBuildNumberCallable(name), greaterThan(oldLastBuildNumber))
        Integer lastBuildNumber = getLastBuildNumber(name)

        // wait until last build finished
        // the build should finish within an hour
        Awaitility.await("checking if build #${lastBuildNumber} is finished")
            .atMost(1, TimeUnit.HOURS)
            .until(isBuildFinishedCallable(name, lastBuildNumber))

        return getBuildInformation(name, lastBuildNumber)
    }

    /**
     * To fetch a job parameters, will start a build without parameters.
     *
     * The job should have a FETCH_PARAMETER_ONLY parameter with default value
     * true, and it should exit the job right after the parameter is fetched.
     * After fetch, all future builds should mark FETCH_PARAMETER_ONLY=false.
     *
     * @param  name   paths to find the job. For example: ['in-my-folder', 'with-job', 'my-branch']
     */
    void fetchJobParameters(List<String> name) {
        logger.finer("Fetching jon ${name} parameters ...")

        startJobAndGetBuildInformation name
    }
}