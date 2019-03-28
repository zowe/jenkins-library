if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library
def _this = this

node ('ibm-jenkins-slave-nvm-jnlp') {
    stage('test') {
        sh 'pwd'
        def github = lib.scm.GitHub.new(_this, [
            'repository'                 : 'zowe/jenkins-library-fvt-nodejs',
            'username'                   : env.GITHUB_USERNAME,
            'email'                      : env.GITHUB_EMAIL,
            'usernamePasswordCredential' : env.GITHUB_CREDENTIAL,
        ])
        echo "github: ${github}"

        github.cloneRepository(['targetFolder': '.tmp-git', 'shallow': true])
        sh 'ls -la .tmp-git'
    }
}
