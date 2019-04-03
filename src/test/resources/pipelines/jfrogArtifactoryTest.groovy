// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for JFrogArtifactory object
def artifactory

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize npm registry and github object
     */
    stage('init') {
        // init artifactory
        artifactory = lib.scm.GitHub.new(this)
        if (!artifactory) {
            error 'Failed to initialize GitHub instance.'
        }
        artifactory.init([
            'url'                        : env.ARTIFACTORY_URL,
            'usernamePasswordCredential' : env.ARTIFACTORY_CREDENTIAL,
        ])

        echo "[JFROG_ARTIFACTORY_TEST] init successfully"
    }

    /**
     * Should be able to get artifact information
     */
    stage('getArtifact') {
        String pattern = 'libs-release-local/org/zowe/1.0.0/zowe-1.0.0.pax'
        String expectedBuildName = 'zowe-promote-publish :: master'
        String expectedBuildNumber = '50'

        Map artifact = artifactory.getArtifact(pattern)
        if (!artifact || !artifact['path']) {
            error "Failed to find \"${pattern}\""
        }

        if (!artifact || !artifact['build.name'] || artifact['build.name'] != expectedBuildName) {
            error "Artifact build name \"${artifact['build.name']}\" is not expected as \"${expectedBuildName}\"."
        }

        if (!artifact || !artifact['build.number'] || artifact['build.number'] != expectedBuildNumber) {
            error "Artifact build number \"${artifact['build.number']}\" is not expected as \"${expectedBuildNumber}\"."
        }

        echo "[JFROG_ARTIFACTORY_TEST] getArtifact successfully"
    }
}
