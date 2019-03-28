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

// the folder name where the repository will be cloned to
def CLONE_DIRECTORY = '.tmp-git'

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
            'username'                   : env.GITHUB_USERNAME,
            'email'                      : env.GITHUB_EMAIL,
            'usernamePasswordCredential' : env.GITHUB_CREDENTIAL,
        ])
    }

    /**
     * Should be able to clone the repository
     */
    stage('clone') {
        github.cloneRepository(['folder': CLONE_DIRECTORY, 'shallow': true])
        echo ">>>>>>>>>> Full list of clone result: >>>>>>>>>>"
        sh "ls -la '${CLONE_DIRECTORY}'"
        // after clone to local, these files should exist
        // - pacakge.json
        // - Jenkinsfile
        if (!fileExists("${CLONE_DIRECTORY}/package.json") ||
            !fileExists("${CLONE_DIRECTORY}/Jenkinsfile")) {
            error 'Failed to clone repository, expected file doesn\'t exist'
        }
        // should be shallow clone
        def logsCount =github.command('git log --oneline | wc -l')
        if (logsCount != '1') {
            error 'There are more than one line of logs, not a shallow clone.'
        }
    }

    /**
     * Should be able to commit changes
     */
    stage('commit') {
        // make modification
        sh "echo '- ${env.JOB_NAME}#${env.BUILD_NUMBER}' >> '${CLONE_DIRECTORY}/test-commit'"
        // commit
        def msg = "Automated commit from ${env.JOB_NAME}#${env.BUILD_NUMBER}"
        github.commit(msg)
        // verify
        if (!github.isClean()) {
            error 'Working tree is not clean. There are changes not committed.'
        }
        // check result
        def commit = getLastCommit(['subject'])
        if (!commit['subject'] || commit['subject'] != msg) {
            error "Failed to verify commit subject:\nCommit: ${commit}\nExpected subject: ${msg}"
        }
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
    }

    /**
     * Should be able to tag branch
     */
    stage('tag') {
        echo "pending"
    }
}
