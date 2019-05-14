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

import java.util.logging.Logger
import org.junit.*
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils
import static groovy.test.GroovyAssert.*

/**
 * Test {@link org.zowe.jenkins_shared_library.pipelines.nodejs.NodeJSPipeline}
 *
 * The test case will create a test Jenkins job and attach the current library to it.
 *
 * Then will run several validations on the job:
 *
 * - start with default parameter and the job should success
 * - test a PATCH release
 */
class IntegrationTest {
    /**
     * JenkinsAPI instance
     */
    static JenkinsAPI jenkins
    /**
     * Logger object
     */
    static transient Logger logger

    /**
     * The test job name. After test is done, the job should be deleted.
     */
    static String testJobName

    /**
     * Full test job name including parent folders
     */
    static List<String> fullTestJobName

    /**
     * Build information
     */
    static Map buildInformation

    /**
     * Build console log
     */
    static String buildLog

    /**
     * Init dependencies
     */
    public static void initDependencies() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())

        // init JenkinsAPI
        jenkins = JenkinsAPI.init()
    }

    /**
     * Init test job name
     * @param  name          prefix of the job name
     */
    public static void _initTestJobName(String name = 'test') {
            // init test job name
        testJobName = "${name}-${Utils.getTimestamp()}"
        fullTestJobName = [Constants.INTEGRATION_TEST_JENKINS_FOLDER, testJobName]
        logger.fine("Test job \"${fullTestJobName}\" will be created for testing")
    }

    /**
     * Create test pipeline job with following steps:
     *
     * - create
     * - start
     * - wait for it's done
     * - get build result
     * - get build console log
     *
     * NOTE: The job should have a build parameter LIBRARY_BRANCH defined.
     *
     * @param  name             prefix of the job name
     * @param  pipeline         pipeline template name to create the job. Optional if git-url/git-credential are provided.
     * @param  git-url          github repository full url. Optional if pipeline is provided.
     * @param  git-credential   github credential
     * @param  git-branch       repository branch. Optional, default is master
     * @param  jenkinsfile-path path to Jenkinsfile. Optional, default is Jenkinsfile
     * @param  env-var          environment variables for the pipeline. optional.
     */
    public static void initPipelineJob(Map args = [:]) throws InvalidArgumentException {
        // validate arguments
        if (!args.containsKey('name') || !args['name']) {
            throw new InvalidArgumentException('name')
        }
        def pipelineScript = false
        def pipelineScm = false
        def scmBranch
        def jenkinsfilePath
        if (args.containsKey('pipeline') && args['pipeline']) {
            pipelineScript = true
        }
        if (args.containsKey('git-url') && args['git-url'] &&
            args.containsKey('git-credential') && args['git-credential']) {
            pipelineScm = true
            scmBranch = (args.containsKey('git-branch') && args['git-branch']) ? args['git-branch'] : 'master'
            jenkinsfilePath = (args.containsKey('jenkinsfile-path') && args['jenkinsfile-path']) ? args['jenkinsfile-path'] : 'Jenkinsfile'
        }
        if (!pipelineScript && !pipelineScm) {
            throw new InvalidArgumentException('pipeline')
        }

        // init dependencies
        initDependencies()

        // init test job name
        _initTestJobName(args['name'])

        // create test job
        def envVars = args.containsKey('env-vars') ? args['env-vars'] : ''
        if (pipelineScript) {
            def script = Utils.loadResource("src/test/resources/pipelines/${args['pipeline']}.groovy")
            jenkins.createJob(
                this.testJobName,
                'pipeline.xml',
                [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
                [
                    'fvt-env-vars'         : Utils.escapeXml(envVars),
                    'fvt-script'           : Utils.escapeXml(script),
                ]
            )
        } else if (pipelineScm) {
            jenkins.createJob(
                this.testJobName,
                'pipeline-git.xml',
                [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
                [
                    'fvt-env-vars'         : Utils.escapeXml(envVars),
                    'fvt-git-url'          : args['git-url'],
                    'fvt-git-credential'   : args['git-credential'],
                    'fvt-git-branch'       : scmBranch,
                    'fvt-jenkinsfile-path' : jenkinsfilePath
                ]
            )
        }

        // start the job, wait for it's done and get build result
        buildInformation = jenkins.startJobAndGetBuildInformation(fullTestJobName, [
            'LIBRARY_BRANCH': System.getProperty('library.branch')
        ])

        // load job console log
        if (buildInformation && buildInformation['number']) {
            buildLog = jenkins.getBuildLog(fullTestJobName, buildInformation['number'])
        }
    }

    /**
     * Create test multi-branch pipeline job with following steps:
     *
     * - create
     * - scan repository
     * - start
     * - wait for it's done
     * - get build result
     * - get build console log
     *
     * NOTE: The pipeline should have build parameter FETCH_PARAMETER_ONLY and LIBRARY_BRANCH defined.
     *
     * @param  name             prefix of the job name
     * @param  git-credential   github credential
     * @param  git-owner        github owner
     * @param  git-repository   github repository
     * @param  branch           repository branch to start a build. optional.
     * @param  jenkinsfile-path path to Jenkinsfile. optional.
     */
    public static void initMultiBranchPipelineJob(Map args = [:]) throws InvalidArgumentException {
        // validate arguments
        if (!args.containsKey('name') || !args['name']) {
            throw new InvalidArgumentException('name')
        }
        if (!args.containsKey('git-credential') || !args['git-credential']) {
            throw new InvalidArgumentException('git-credential')
        }
        if (!args.containsKey('git-owner') || !args['git-owner']) {
            throw new InvalidArgumentException('git-owner')
        }
        if (!args.containsKey('git-repository') || !args['git-repository']) {
            throw new InvalidArgumentException('git-repository')
        }

        // init dependencies
        initDependencies()

        // init test job name
        _initTestJobName(args['name'])

        // create test job
        jenkins.createJob(
            this.testJobName,
            'multibranchPipeline.xml',
            [Constants.INTEGRATION_TEST_JENKINS_FOLDER],
            [
                'fvt-git-credential'   : args['git-credential'],
                'fvt-git-owner'        : args['git-owner'],
                'fvt-git-repository'   : args['git-repository'],
                'fvt-jenkinsfile-path' : args.containsKey('jenkinsfile-path') ? args['jenkinsfile-path'] : 'Jenkinsfile',
            ]
        )

        // which branch we will test?
        String branch = args.containsKey('branch') ? args['branch'] : 'master'

        // fetch parameters for the branch
        List job = fullTestJobName.collect()
        job.add(branch)
        jenkins.fetchJobParameters(job)

        // start the job, wait for it's done and get build result
        buildInformation = jenkins.startJobAndGetBuildInformation(job, [
            'FETCH_PARAMETER_ONLY' : 'false',
            'LIBRARY_BRANCH'       : System.getProperty('library.branch')
        ])

        // load job console log
        if (buildInformation && buildInformation['number']) {
            buildLog = jenkins.getBuildLog(job, buildInformation['number'])
        }
    }
}
