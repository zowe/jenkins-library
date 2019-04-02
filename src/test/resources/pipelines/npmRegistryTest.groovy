// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for npm registry object
def npmRegistry

// global var for github object
def github

// this repository will be used for testing
def TEST_OWNER = 'zowe'
// this repository will be used for testing
def TEST_REPORSITORY = 'jenkins-library-fvt-nodejs'
// branch to run test
def TEST_BRANCH = 'master'

// expected scope
def EXPECTED_SCOPE = 'zowe'
// expected registry
def EXPECTED_REGISTRY = 'https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-local-release/'

def lastVersionChecked

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize npm registry and github object
     */
    stage('init') {
        // init github
        github = lib.scm.GitHub.new(this)
        if (!github) {
            error 'Failed to initialize GitHub instance.'
        }
        github.init([
            'repository'                 : "${TEST_OWNER}/${TEST_REPORSITORY}",
            'branch'                     : TEST_BRANCH,
            'username'                   : env.GITHUB_USERNAME,
            'email'                      : env.GITHUB_EMAIL,
            'usernamePasswordCredential' : env.GITHUB_CREDENTIAL,
        ])

        // checkout code
        github.cloneRepository()

        // init npm registry
        // must run after code is cloned to workspace
        npmRegistry = lib.npm.Registry.new(this)
        if (!npmRegistry) {
            error 'Failed to initialize npm Registry instance.'
        }
        npmRegistry.init([
            'email'                      : env.NPM_EMAIL,
            'tokenCredential'            : env.NPM_CREDENTIAL,
        ])

        echo "[NPM_REGISTRY_TEST] init successfully"
    }

    /**
     * Should be able to get correct package information
     */
    stage('check-info') {
        String registry = npmRegistry.getRegistry()
        if (registry != EXPECTED_REGISTRY) {
            error "NPM scope \"${registry}\" is not expected as \"${EXPECTED_REGISTRY}\"."
        }

        Map info = npmRegistry.getPackageInfo()
        if (!info.containsKey('scope') || info['scope'] != EXPECTED_SCOPE) {
            error "NPM scope \"${info['scope']}\" is not expected as \"${EXPECTED_SCOPE}\"."
        }
        if (!info.containsKey('versionTrunks')) {
            error "NPM version trunks are not correctly extracted from package.json."
        }
        lastVersionChecked = info['versionTrunks']

        echo "[NPM_REGISTRY_TEST] check-info successfully"
    }

    /**
     * Should be able to login
     */
    stage('login') {
        String whoami = npmRegistry.login()
        if (whoami != env.NPM_USERNAME) {
            error "NPM user name \"${whoami}\" is not expected as \"${env.NPM_USERNAME}\"."
        }

        echo "[NPM_REGISTRY_TEST] login successfully"
    }

    /**
     * Should be able to do patch version bump
     */
    stage('patch') {
        // bump version
        npmRegistry.version(github, TEST_BRANCH, 'PATCH')
        // reset local repository to remote
        github.reset()
        // reload package info
        Map info = npmRegistry.getPackageInfo()
        if (!info.containsKey('versionTrunks')) {
            error "NPM version trunks are not correctly extracted from package.json."
        }
        newVersionChecked = info['versionTrunks']
        if (newVersionChecked['major'] !== lastVersionChecked['major'] ||
            ) {
            error "Patch level versioning does not properly bumped from \"${lastVersionChecked}\" to \"${newVersionChecked}\"."
        }

        echo "[NPM_REGISTRY_TEST] patch successfully"
    }
}
