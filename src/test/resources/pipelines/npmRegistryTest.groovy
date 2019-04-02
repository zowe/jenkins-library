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

// the folder name where the repository will be cloned to
def CLONE_DIRECTORY = '.tmp-git'

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize npm registry and github object
     */
    stage('init') {
        // init npm registry
        npmRegistry = lib.npm.Registry.new(this)
        if (!npmRegistry) {
            error 'Failed to initialize npm Registry instance.'
        }
        npmRegistry.init([
            'email'                      : env.NPM_EMAIL,
            'tokenCredential'            : env.NPM_CREDENTIAL,
        ])

        // init github
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

        echo "[NPM_REGISTRY_TEST] init successfully"
    }

    /**
     * Should be able to clone the repository
     */
    stage('check-info') {
        String registry = npmRegistry.getRegistry()
        if (registry != EXPECTED_REGISTRY) {
            error 'NPM registry is not correctly extracted from package.json.'
        }

        Map info = npmRegistry.getPackageInfo()
        if (!info.containsKey('scope') || info['scope'] != EXPECTED_SCOPE) {
            error 'NPM scope is not correctly extracted from package.json.'
        }

        echo "[NPM_REGISTRY_TEST] check-info successfully"
    }
}
