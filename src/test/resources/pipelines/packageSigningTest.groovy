// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for package/Signing object
def signing

// test constants
String TEST_ASCII_FILE    = "test-ascii.txt"

node ('zowe-jenkins-agent') {
    /**
     * Initialize package/Signing object
     */
    stage('init') {
        // init artifactory
        signing = lib.package.Signing.new(this)
        if (!signing) {
            error 'Failed to initialize package/Signing instance.'
        }
        signing.init([
            'gpgKeyPassPhrase'       : env.CODE_SIGNING_KEY_PASSPHRASE,
            'gpgPrivateKey'          : env.CODE_SIGNING_PRIVATE_KEY_FILE,
        ])

        // prepare file
        sh "echo \"Current time: ${lib.Utils.getTimestamp()}\" > ${TEST_ASCII_FILE}"

        echo "[PACKAGE_SIGNING_TEST] init successfully"
    }

    /**
     * Should be able to sign a file
     */
    stage('sign') {
        def result = signing.sign("${TEST_ASCII_FILE}")

        if (result != "${TEST_ASCII_FILE}.asc") {
            error "Signing is not successful."
        }

        echo "[PACKAGE_SIGNING_TEST] sign successfully"
    }

    /**
     * Should be able to verify signature
     *
     * Use the signature created in last stage
     */
    stage('verifySignature') {
        def result = signing.verifySignature("${TEST_ASCII_FILE}")

        if (!result) {
            error "Verifying signature failed."
        }

        echo "[PACKAGE_SIGNING_TEST] verifySignature successfully"
    }

    /**
     * Should be able to sign a file
     */
    stage('hash') {
        def result = signing.hash("${TEST_ASCII_FILE}")
        def algo = signing.getDEFAULT_HASH_ALGORITHM().toLowerCase()

        if (result != "./${TEST_ASCII_FILE}.${algo}") {
            error "Generating hash is not successful."
        }

        def hashContent = this.steps.sh(
            script: "cat \"${result}\"",
            returnStdout: true
        ).trim()
        echo "Hash file content:\n${hashContent}"
        if (!hashContent.contains(TEST_ASCII_FILE)) {
            error "Hash file is not valid."
        }

        echo "[PACKAGE_SIGNING_TEST] hash successfully"
    }
}
