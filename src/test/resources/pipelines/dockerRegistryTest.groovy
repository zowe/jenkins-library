// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for docker registry object
def dockerRegistry

// this repository will be used for testing
def TEST_OWNER        = 'zowe'
// this repository will be used for testing
def TEST_REPORSITORY  = 'jenkins-library-fvt-nodejs'
// branch to run test
def TEST_BRANCH       = 'master'
// docker image to publish
def TEST_IMAGE        = "${env.DOCKER_IMAGE_REPFIX}/${TEST_REPORSITORY}"
// image tag
def TEST_TAG          = "${env.JOB_NAME}-${env.BUILD_NUMBER}".replaceAll(/[^0-9a-zA-Z\-]/, '-')

// requires DinD image
node ('ibm-jenkins-slave-dind') {
    /**
     * Initialize docker registry and github object
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

        // init docker registry
        // must run after code is cloned to workspace
        dockerRegistry = lib.docker.Registry.new(this)
        if (!dockerRegistry) {
            error 'Failed to initialize docker Registry instance.'
        }
        dockerRegistry.init([
            'url'                        : env.DOCKER_REGISTRY,
            'usernamePasswordCredential' : env.DOCKER_CREDENTIAL,
        ])

        echo "[DOCKER_REGISTRY_TEST] init successfully"
    }

    /**
     * Should be able to build
     */
    stage('build') {
        dockerRegistry.build('Dockerfile')

        echo "Docker images list:"
        sh "docker images"

        echo "[DOCKER_REGISTRY_TEST] build successfully"
    }

    /**
     * Should be able to publish
     */
    stage('publish') {
        dockerRegistry.publish(TEST_IMAGE, TEST_TAG)

        echo "[DOCKER_REGISTRY_TEST] publish successfully"
    }

    /**
     * Should be able to publish
     */
    stage('pull-run') {
        echo "Cleaning images ..."
        dockerRegistry.clean()

        dockerRegistry.within {
            def fullImageName = dockerRegistry.getFullImageName(TEST_TAG)
            def result = sh(script: "docker run ${fullImageName}", returnStdout: true).trim()
            if (result != 'Hello Zowe') {
                error 'Failed to start container. Output is: ' + result
            }
        }

        echo "[DOCKER_REGISTRY_TEST] pull and run successfully"
    }
}
