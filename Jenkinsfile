#!groovy

/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

def isPullRequest = env.CHANGE_ID ? true : false

// only when building on these branches, docs will be updated
def DOCS_UPDATE_BRANCHES = ['master', 'classes']

// constants will be used for testing
def JENKINS_CREDENTIAL     = 'jenkins-credential'
def GITHUB_USERNAME        = 'Zowe Robot'
def GITHUB_EMAIL           = 'zowe.robot@gmail.com'
def GITHUB_CREDENTIAL      = 'zowe-robot-github'
def NPM_USERNAME           = 'jenkins'
def NPM_EMAIL              = 'zowe.robot@gmail.com'
def NPM_CREDENTIAL         = 'zowe.jfrog.io'
def ARTIFACTORY_URL        = 'https://zowe.jfrog.io/zowe'
def ARTIFACTORY_CREDENTIAL = 'zowe.jfrog.io'
def PAX_SERVER_HOST        = 'zzow01.zowe.marist.cloud'
def PAX_SERVER_PORT        = 22
def PAX_SERVER_CREDENTIAL  = 'ssh-marist-server-zzow01'
def SIGNING_KEY_PASSPHRASE = 'code-signing-key-passphrase-jack'
def SIGNING_KEY_FILE       = 'code-signing-key-private-jack'
def DOCKER_REGISTRY        = ''
def DOCKER_CREDENTIAL      = 'jackjia-docker-access-token'
def DOCKER_IMAGE_REPFIX    = 'jackjiaibm'

def opts = []
// keep last 20 builds for regular branches, no keep for pull requests
opts.push(buildDiscarder(logRotator(numToKeepStr: (isPullRequest ? '' : '20'))))
// disable concurrent build
opts.push(disableConcurrentBuilds())

// define custom build parameters
def customParameters = []
customParameters.push(choice(
    name: 'TEST_LOG_LEVEL',
    choices: ['', 'SEVERE', 'WARNING', 'INFO', 'CONFIG', 'FINE', 'FINER', 'FINEST'],
    description: 'Log level for running gradle test. Default is INFO if leave it empty.'
))
customParameters.push(string(
    name: 'TEST_CATEGORY',
    description: 'What set of tests want to run',
    defaultValue: '',
    trim: true
))
opts.push(parameters(customParameters))

// set build properties
properties(opts)

node('ibm-jenkins-slave-nvm') {
  currentBuild.result = 'SUCCESS'

  try {
    stage('checkout') {
        // checkout source code
        checkout scm

        // bootstrap gradle
        sh "./bootstrap_gradlew.sh"

        // check if it's pull request
        echo "Current branch is ${env.BRANCH_NAME}"
        if (isPullRequest) {
          echo "This is a pull request: merge ${env.CHANGE_BRANCH} into ${env.CHANGE_TARGET}"
        }
    }

    stage('test') {
        try {
            def branch = env.BRANCH_NAME
            if (isPullRequest) {
              branch = env.CHANGE_BRANCH
            }

            withCredentials([usernamePassword(
                credentialsId: JENKINS_CREDENTIAL,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )]) {
                sh "./gradlew test" +
                   " -PlogLevel=${params.TEST_LOG_LEVEL}" +
                   " -Pjenkins.baseuri='${env.JENKINS_URL}'" +
                   " -Pjenkins.user='${USERNAME}'" +
                   " -Pjenkins.password='${PASSWORD}'" +
                   " -Plibrary.branch='${branch}'" +
                   " -Pgithub.username='${GITHUB_USERNAME}'" +
                   " -Pgithub.email='${GITHUB_EMAIL}'" +
                   " -Pgithub.credential='${GITHUB_CREDENTIAL}'" +
                   " -Pnpm.username='${NPM_USERNAME}'" +
                   " -Pnpm.email='${NPM_EMAIL}'" +
                   " -Pnpm.credential='${NPM_CREDENTIAL}'" +
                   " -Partifactory.url='${ARTIFACTORY_URL}'" +
                   " -Partifactory.credential='${ARTIFACTORY_CREDENTIAL}'" +
                   " -Ppax.server.host='${PAX_SERVER_HOST}'" +
                   " -Ppax.server.port='${PAX_SERVER_PORT}'" +
                   " -Ppax.server.crdential='${PAX_SERVER_CREDENTIAL}'" +
                   " -Psigning.key.passphrase='${SIGNING_KEY_PASSPHRASE}'" +
                   " -Psigning.key.file='${SIGNING_KEY_FILE}'" +
                   " -Pdocker.email='${DOCKER_REGISTRY}'" +
                   " -Pdocker.credential='${DOCKER_CREDENTIAL}'" +
                   " -Pdocker.imageprefix='${DOCKER_IMAGE_REPFIX}'" +
                   (params.TEST_CATEGORY ? " --tests '" + params.TEST_CATEGORY + "'": "")
            }
        } catch (e) {
            throw e
        } finally {
            // publish any test results found
            junit allowEmptyResults: true, testResults: '**/test-results/**/*.xml'

            // publish test html report
            publishHTML(target: [
                allowMissing         : false,
                alwaysLinkToLastBuild: false,
                keepAll              : true,
                reportDir            : 'build/reports/tests/test',
                reportFiles          : 'index.html',
                reportName           : "Unit Test Results"
            ])
        }
    }

    stage('render-doc') {
        if (!DOCS_UPDATE_BRANCHES.contains(env.BRANCH_NAME)) {
            echo "Skip building documentation for branch ${env.BRANCH_NAME}"
            return
        }

        // generate doc
        sh './gradlew groovydoc'

        // env.BRANCH_NAME
        dir('build/docs/groovydoc') {
            // init git folder
            sh "git init\n" +
               "git remote add origin https://github.com/zowe/jenkins-library\n" +
               "git fetch origin\n" +
               "git reset origin/gh-pages\n"
            // check if there are changes on docs
            def docsUpdated = sh(script: "git status --porcelain", returnStdout: true).trim()
            if (docsUpdated) {
                echo "These documentation are changed:\n${docsUpdated}"
                // commit changes
                sh "git config user.email \"${GITHUB_EMAIL}\"\n" +
                   "git config user.name \"${GITHUB_USERNAME}\"\n" +
                   "git add .\n" +
                   "git commit -s -m \"Updating docs from ${env.JOB_NAME}#${env.BUILD_NUMBER}\"\n"
                // push changes
                withCredentials([
                    usernamePassword(
                        credentialsId    : GITHUB_CREDENTIAL,
                        passwordVariable : 'PASSWORD',
                        usernameVariable : 'USERNAME'
                    )
                ]) {
                    sh "git push https://${USERNAME}:${PASSWORD}@github.com/zowe/jenkins-library HEAD:gh-pages"
                    echo "Documentation updated."
                }
            }
        }
    }

    stage('done') {
      // send out notification
      emailext body: "Job \"${env.JOB_NAME}\" build #${env.BUILD_NUMBER} success.\n\nCheck detail: ${env.BUILD_URL}" ,
          subject: "[Jenkins] Job \"${env.JOB_NAME}\" build #${env.BUILD_NUMBER} success",
          recipientProviders: [
            [$class: 'RequesterRecipientProvider'],
            [$class: 'CulpritsRecipientProvider'],
            [$class: 'DevelopersRecipientProvider'],
            [$class: 'UpstreamComitterRecipientProvider']
          ]
    }

  } catch (err) {
    currentBuild.result = 'FAILURE'

    // catch all failures to send out notification
    emailext body: "Job \"${env.JOB_NAME}\" build #${env.BUILD_NUMBER} failed.\n\nError: ${err}\n\nCheck detail: ${env.BUILD_URL}" ,
        subject: "[Jenkins] Job \"${env.JOB_NAME}\" build #${env.BUILD_NUMBER} failed",
        recipientProviders: [
          [$class: 'RequesterRecipientProvider'],
          [$class: 'CulpritsRecipientProvider'],
          [$class: 'DevelopersRecipientProvider'],
          [$class: 'UpstreamComitterRecipientProvider']
        ]

    throw err
  }
}
