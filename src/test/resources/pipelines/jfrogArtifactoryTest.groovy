// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for JFrogArtifactory object
def jfrog

// test constants
String testLocalArtifact  = ".tmp-artifact"
String testRemoteArtifact = "test-artifactory-upload.txt"
Integer testPropValue     = 1
String snapshotArtifact   = "libs-snapshot-local/org/zowe/jenkins-library-test/${testRemoteArtifact}"
String releaseFolder      = "libs-release-local/org/zowe/jenkins-library-test/"

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize JFrogArtifactory object
     */
    stage('init') {
        // init artifactory
        jfrog = lib.artifact.JFrogArtifactory.new(this)
        if (!jfrog) {
            error 'Failed to initialize JFrogArtifactory instance.'
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
     * Should be able to get build information
     */
    stage('getBuildInfo') {
        // this is parent build of `libs-release-local/org/zowe/1.0.0/zowe-1.0.0.pax`
        String buildName           = 'zowe-install-packaging :: master'
        String buildNumber         = '515'
        String expectedVcsRevision = 'f11489d588321281a461eb7bc7883b495f16d882'

        // get build
        Map build = jfrog.getBuildInfo(buildName, buildNumber)

        // validate resolved build name
        if (!build || !build['name']) {
            error "Failed to find \"${buildName}/${buildNumber}\""
        }

        // validate build name
        if (!build || !build['name'] || build['name'] != buildName) {
            error "build name \"${build['name']}\" is not expected as \"${buildName}\"."
        }

        // validate build vcsRevision
        if (!build || !build['vcsRevision'] || build['vcsRevision'] != expectedVcsRevision) {
            error "build vcsRevision \"${build['vcsRevision']}\" is not expected as \"${expectedVcsRevision}\"."
        }

        echo "[JFROG_ARTIFACTORY_TEST] getBuildInfo successfully"
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
        // prepare artifact
        sh "echo test > ${testLocalArtifact}"

        // upload the artifact
        jfrog.upload(testLocalArtifact, snapshotArtifact, [
            'test.key': testPropValue
        ])

        // get artifact
        Map artifact = jfrog.getArtifact(snapshotArtifact)
        // NOTE: property value should be converted to string
        if (!artifact || artifact['test.key'] != "${testPropValue}") {
            error 'Artifact property "test.key" is not set correctly.'
        }

        echo "[JFROG_ARTIFACTORY_TEST] upload successfully"
    }

    /**
     * Should be able to promote artifact
     *
     * Will use artifact uploaded from upload stage
     */
    stage('promote') {
        def ts = lib.Utils.getTimestamp()
        releaseFolder += ts + '/'

        def result = jfrog.promote(snapshotArtifact, releaseFolder)

        if (result != "${releaseFolder}${testRemoteArtifact}") {
            error "Promote result \"${result}\" is not as expected \"${releaseFolder}${testRemoteArtifact}\"."
        }

        echo "[JFROG_ARTIFACTORY_TEST] promote successfully"
    }
}
