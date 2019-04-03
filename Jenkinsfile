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

def isPullRequest = env.BRANCH_NAME.startsWith('PR-')

// constants will be used for testing
def JENKINS_CREDENTIAL = 'jenkins-credential'
def GITHUB_USERNAME = 'Zowe Robot'
def GITHUB_EMAIL = 'zowe.robot@gmail.com'
def GITHUB_CREDENTIAL = 'zowe-robot-github'
def NPM_USERNAME = 'giza-jenkins'
def NPM_EMAIL = 'giza-jenkins@gmail.com'
def NPM_CREDENTIAL= 'giza-jenkins-basicAuth'

// other constants
def CI_SKIP = '[ci skip]'

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
opts.push(parameters(customParameters))

// set build properties
properties(opts)

node ('ibm-jenkins-slave-nvm-jnlp') {
    stage('checkout') {
        // checkout source code
        checkout scm

        // check if we should skip the build
        currentBuild.changeSets.each{ changeSet ->
          echo "Changeset: ${changeSet} - ${changeSet.getKind()}"
          changeSet.each{ entry ->
            echo "entry: ${entry.getMsg()}"
          }
        }
        // def lastCommit = sh(script: "git show --format=\"%s :: %b\" -s HEAD", returnStdout: true).trim()
        // if (lastCommit.contains(CI_SKIP)) {
        //   // CI_SKIP spotted in the git commit
        //   currentBuild.result = 'NOT_BUILT'
        //   error("Skipped due to last commit marked as ${CI_SKIP}")
        // }

        error "aborting..."

        // check if it's pull request
        echo "Current branch is ${env.BRANCH_NAME}"
        if (isPullRequest) {
          echo "This is a pull request"
        }
    }

    stage('test') {
        try {
            withCredentials([usernamePassword(
                credentialsId: JENKINS_CREDENTIAL,
                passwordVariable: 'PASSWORD',
                usernameVariable: 'USERNAME'
            )]) {
                sh """
./gradlew test \
  -PlogLevel=${params.TEST_LOG_LEVEL} \
  -Pjenkins.baseuri='${env.JENKINS_URL}' \
  -Pjenkins.user='${USERNAME}' \
  -Pjenkins.password='${PASSWORD}' \
  -Plibrary.branch='${env.BRANCH_NAME}' \
  -Pgithub.username='${GITHUB_USERNAME}' \
  -Pgithub.email='${GITHUB_EMAIL}' \
  -Pgithub.credential='${GITHUB_CREDENTIAL}' \
  -Pnpm.username='${NPM_USERNAME}' \
  -Pnpm.email='${NPM_EMAIL}' \
  -Pnpm.credential='${NPM_CREDENTIAL}'
"""
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
        // generate doc
        sh './gradlew groovydoc'
        // check if there are changes on docs
        def docsUpdated = sh(script: "git status --porcelain | grep docs", returnStdout: true).trim()
        if (docsUpdated) {
          echo "These documentation are changed:\n${docsUpdated}"
          // commit changes
          sh """git config --global user.email "${GITHUB_EMAIL}"
git config --global user.name "${GITHUB_USERNAME}"
git add docs
git commit -m \"Updating docs from ${env.JOB_NAME}#${env.BUILD_NUMBER} ${CI_SKIP}\"
"""
          // push changes
          withCredentials([
            usernamePassword(
              credentialsId    : GITHUB_CREDENTIAL,
              passwordVariable : 'PASSWORD',
              usernameVariable : 'USERNAME'
            )
          ]) {
            sh "git push https://${USERNAME}:${PASSWORD}@github.com/zowe/jenkins-library HEAD:${env.BRANCH_NAME}"
            echo "Documentation updated."
          }
        }
    }
}
