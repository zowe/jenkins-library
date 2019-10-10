// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for github object
def github

// this repository will be used for testing
def TEST_REPORSITORY = 'zowe/jenkins-library-fvt-nodejs'
// branch to run test
def TEST_BRANCH      = 'master'

// the folder name where the repository will be cloned to
def CLONE_DIRECTORY  = '.tmp-git'

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize github object
     */
    stage('init') {
        github = lib.scm.GitHub.new(this)
        if (!github) {
            error 'Failed to initialize GitHub instance.'
        }
        github.init([
            'repository'                 : TEST_REPORSITORY,
            'branch'                     : TEST_BRANCH,
            'folder'                     : CLONE_DIRECTORY,
            'username'                   : env.GITHUB_USERNAME,
            'email'                      : env.GITHUB_EMAIL,
            'usernamePasswordCredential' : env.GITHUB_CREDENTIAL,
        ])

        echo "[GITHUB_TEST] init successfully"
    }

    /**
     * Should be able to clone the repository
     */
    stage('clone') {
        github.cloneRepository()
        echo ">>>>>>>>>> Full list of clone result: >>>>>>>>>>"
        sh "ls -la '${CLONE_DIRECTORY}'"
        // after clone to local, these files should exist
        // - package.json
        // - Jenkinsfile
        if (!fileExists("${CLONE_DIRECTORY}/package.json") ||
            !fileExists("${CLONE_DIRECTORY}/Jenkinsfile")) {
            error 'Failed to clone repository, expected file doesn\'t exist'
        }

        echo "[GITHUB_TEST] clone successfully"
    }

    /**
     * Should be able to commit changes
     */
    stage('commit') {
        // make modification
        sh "echo '- ${env.JOB_NAME}#${env.BUILD_NUMBER}' >> '${CLONE_DIRECTORY}/test-commit.txt'"
        // commit
        def msg = "Automated commit from ${env.JOB_NAME}#${env.BUILD_NUMBER}"
        github.commit(msg)
        // verify
        if (!github.isClean()) {
            error 'Working tree is not clean. There are changes not committed.'
        }
        // check result
        def commit = github.getLastCommit(['subject'])
        if (!commit['subject'] || commit['subject'] != msg) {
            error "Failed to verify commit subject:\nCommit: ${commit}\nExpected subject: ${msg}"
        }

        echo "[GITHUB_TEST] commit successfully"
    }

    /**
     * Should be able to push changes
     */
    stage('push') {
        // push changes we committed from last stage
        github.push()
        if (!github.isSynced()) {
            error 'Branch is not synced with remote, there are commits not pushed or remote have been updated.'
        }

        echo "[GITHUB_TEST] push successfully"
    }

    /**
     * Should be able to tag branch
     */
    stage('tag') {
        def tag = "${env.JOB_NAME}#${env.BUILD_NUMBER}".replaceAll("[^a-zA-Z0-9]", '-')
        github.tag(['tag': tag])

        if (!github.tagExistsLocal(tag)) {
            error 'Tag is not created on local.'
        }
        if (!github.tagExistsRemote(tag)) {
            error 'Tag is not created on remote.'
        }

        echo "[GITHUB_TEST] tag successfully"
    }

    /**
     * Should be able to create pull request
     */
    stage('pull-request') {
        // prepare the test branch
        def testBranch = "test/pr_${lib.Utils.getTimestamp()}".toString()
        github.checkout(testBranch, true)
        sh "echo '- ${env.JOB_NAME}#${env.BUILD_NUMBER}: on branch ${testBranch}' >> '${CLONE_DIRECTORY}/test-commit.txt'"
        github.commit("test commit for creating pr on branch ${testBranch}")
        github.push()

        // create pull request against master
        def prId = github.createPullRequest('master', "Test pull request on branch ${testBranch}")

        if (!prId || prId <= 0) {
            error 'Pull request is not created.'
        }
        echo "Pull request #${prId} is created."

        // closing after test
        github.closePullRequest(prId)
        github.deleteRemoteBranch()

        echo "[GITHUB_TEST] creating pull request successfully"
    }
}
