def execute() {
    def _this = this

    node() {
        stage('test') {
            sh 'pwd'
            def github = new org.zowe.jenkins_shared_library.scm.GitHub(_this, [
                'repository'                 : 'zowe/jenkins-library-fvt-nodejs',
                'username'                   : GITHUB_USERNAME,
                'email'                      : GITHUB_EMAIL,
                'usernamePasswordCredential' : GITHUB_CREDENTIAL,
            ])
            echo "github: ${github}"

            github.cloneRepository(['targetFolder': '.tmp-git', 'shallow': true])
            sh 'ls -la .tmp-git'
        }
    }
}

return this
