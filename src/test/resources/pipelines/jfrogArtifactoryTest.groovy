// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for JFrogArtifactory object
def jfrog

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize JFrogArtifactory object
     */
    stage('init') {
        // init artifactory
        jfrog = lib.artifact.JFrogArtifactory.new(this)
        if (!jfrog) {
            error 'Failed to initialize GitHub instance.'
        }
        jfrog.init([
            'url'                        : env.ARTIFACTORY_URL,
            'usernamePasswordCredential' : env.ARTIFACTORY_CREDENTIAL,
        ])

        echo "[JFROG_ARTIFACTORY_TEST] init successfully"
    }

    /**
     * Should be able to get artifact information
     */
    stage('getArtifact') {
        String pattern             = 'libs-release-local/org/zowe/1.0.0/zowe-1.0.0.pax'
        String expectedBuildName   = 'zowe-promote-publish :: master'
        String expectedBuildNumber = '50'

        // get artifact
        Map artifact = jfrog.getArtifact(pattern)

        // validate resolved artifact path
        if (!artifact || !artifact['path']) {
            error "Failed to find \"${pattern}\""
        }

        // validate build name
        if (!artifact || !artifact['build.name'] || artifact['build.name'] != expectedBuildName) {
            error "Artifact build name \"${artifact['build.name']}\" is not expected as \"${expectedBuildName}\"."
        }

        // validate build number
        if (!artifact || !artifact['build.number'] || artifact['build.number'] != expectedBuildNumber) {
            error "Artifact build number \"${artifact['build.number']}\" is not expected as \"${expectedBuildNumber}\"."
        }

        echo "[JFROG_ARTIFACTORY_TEST] getArtifact successfully"
    }

    /**
     * Should be able to download artifacts
     */
    stage('download') {
        String downloadFolder = ".tmp-artifacts"
        String spec = """{
    "files": [
        {
            "pattern": "libs-release-local/org/zowe/explorer-jes/0.0.*/explorer-jes-0.0.*.pax",
            "target": "${downloadFolder}/",
            "flat": "true",
            "sortBy": ["created"],
            "sortOrder": "desc",
            "limit": 1
        },
        {
            "pattern": "libs-release-local/org/zowe/explorer-mvs/0.0.*/explorer-mvs-0.0.*.pax",
            "target": "${downloadFolder}/",
            "flat": "true",
            "sortBy": ["created"],
            "sortOrder": "desc",
            "limit": 1
        }
    ]
}
"""
        Integer expected = 2

        // download the artifacts
        jfrog.download(specContent: spec, expected: expected)

        // verify downloaded files
        def downloaded = sh(
            script: "ls -1 ${downloadFolder} | wc -l",
            returnStdout: true
        ).trim()
        if (downloaded != "${expected}") {
            error "Failed to download expected artifacts."
        }

        echo "[JFROG_ARTIFACTORY_TEST] download successfully"
    }

    /**
     * Should be able to upload artifact
     */
    stage('upload') {
        String testArtifact = ".tmp-artifact"
        sh "echo test > ${testArtifact}"
        def target = "libs-snapshot-local/org/zowe/jenkins-library-test/test-artifactory-upload.txt"

        // upload the artifact
        jfrog.upload(testArtifact, target)

        echo "[JFROG_ARTIFACTORY_TEST] upload successfully"
    }
}
