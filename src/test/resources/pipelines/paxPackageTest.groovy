// load specific library branch
if (!params.LIBRARY_BRANCH) {
    error 'LIBRARY_BRANCH parameter is required to start the test pipeline'
}
echo "Jenkins library branch ${params.LIBRARY_BRANCH} will be used to build."
def lib = library("jenkins-library@${params.LIBRARY_BRANCH}").org.zowe.jenkins_shared_library

// global var for package/Pax object
def pax

// test constants
String TEST_JOB_NAME      = "library-test"
String TEST_BINARY_FILE   = 'bash'
String TEST_ASCII_FILE    = "test-ascii.txt"
String TEST_ASCII_CONTENT = "this should be human readable"
String TEST_ENV_VAR_NAME  = 'LIBRARY_TEST_SAMPLE_VAR'
String TEST_ENV_VAR_VALUE = '1234'

node ('ibm-jenkins-slave-nvm-jnlp') {
    /**
     * Initialize package/Pax object
     */
    stage('init') {
        // init artifactory
        pax = lib.package.Pax.new(this)
        if (!pax) {
            error 'Failed to initialize package/Pax instance.'
        }
        pax.init([
            'sshHost'          : env.PAX_SERVER_HOST,
            'sshPort'          : env.PAX_SERVER_PORT,
            'sshCredential'    : env.PAX_SERVER_CREDENTIAL,
        ])

        // prepare local workspace
        def localWorkspace = pax.getLocalWorkspace()
        def pathContent = pax.getPATH_CONTENT()
        def pathAscii = pax.getPATH_ASCII()
        def hookPrepareWorkspace = pax.getHOOK_PREPARE_WORKSPACE()
        def hookPrePackaging = pax.getHOOK_PRE_PACKAGING()
        def hookPostPackaging = pax.getHOOK_POST_PACKAGING()
        sh "mkdir -p ${localWorkspace}/${pathContent}"
        sh "mkdir -p ${localWorkspace}/${pathAscii}"
        // write prepare hook
        writeFile file: "${localWorkspace}/${hookPrepareWorkspace}", text: """
echo "[${hookPrepareWorkspace}] started ..."
echo "[${hookPrepareWorkspace}] pwd=\$(pwd)"

echo "[${hookPrepareWorkspace}] prepare a binary file ..."
WHICH_BINARY=\$(which ${TEST_BINARY_FILE})
cp \$WHICH_BINARY ${localWorkspace}/${pathContent}/

echo "[${hookPrepareWorkspace}] prepare a text file ..."
echo "${TEST_ASCII_CONTENT}" > ${localWorkspace}/${pathAscii}/${TEST_ASCII_FILE}

echo "[${hookPrepareWorkspace}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"

echo "[${hookPrepareWorkspace}] ended."
"""

        // write pre hook
        writeFile file: "${localWorkspace}/${hookPrePackaging}", text: """
echo "[${hookPrePackaging}] started ..."
echo "[${hookPrePackaging}] pwd=\$(pwd)"
echo "[${hookPrePackaging}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"
echo "[${hookPrePackaging}] ended."
"""

        // write post hook
        writeFile file: "${localWorkspace}/${hookPostPackaging}", text: """
echo "[${hookPostPackaging}] started ..."
echo "[${hookPostPackaging}] pwd=\$(pwd)"
echo "[${hookPostPackaging}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"
echo "[${hookPostPackaging}] ended."
"""

        echo "[PAX_PACKAGE_TEST] init successfully"
    }

    /**
     * Should be able to create a PAX package
     */
    stage('package') {
        def result = pax.pack(TEST_JOB_NAME, "${TEST_JOB_NAME}.pax", ["${TEST_ENV_VAR_NAME}": TEST_ENV_VAR_VALUE])

        def localWorkspace = pax.getLocalWorkspace()
        if (result != "${localWorkspace}/${TEST_JOB_NAME}.pax") {
            error "Pax pack result \"$result\" is not expected \"${localWorkspace}/${TEST_JOB_NAME}.pax\"."
        }
        if (!fileExists("${localWorkspace}/${TEST_JOB_NAME}.pax")) {
            error 'Failed to find the expected package'
        }

        echo "[PAX_PACKAGE_TEST] pack successfully"
    }

    /**
     * Should be able to unpack a PAX package
     *
     * Use the PAX created in last stage
     */
    stage('unpack') {
        def localWorkspace = pax.getLocalWorkspace()
        def remoteWorkspace = pax.getRemoteWorkspace()
        def remoteWorkspaceFullPath = "${remoteWorkspace}/test-unpack-${lib.Utils.getTimestamp()}"

        try {
            withCredentials([
                usernamePassword(
                    credentialsId    : env.PAX_SERVER_CREDENTIAL,
                    passwordVariable : 'PASSWORD',
                    usernameVariable : 'USERNAME'
                )
            ]) {
                // create remote workspace
                sh "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${env.PAX_SERVER_PORT} \${USERNAME}@${env.PAX_SERVER_HOST} \"mkdir -p ${remoteWorkspaceFullPath}\""

                def result = pax.unpack("${localWorkspace}/${TEST_JOB_NAME}.pax", remoteWorkspaceFullPath)
                if (!result.contains("${remoteWorkspaceFullPath}/${TEST_BINARY_FILE}") || !result.contains("${remoteWorkspaceFullPath}/${TEST_ASCII_FILE}")) {
                    error "Unpack result doesn't not contain \"${TEST_BINARY_FILE}\" or \"${TEST_ASCII_FILE}\": ${result}"
                }

                def textFileContent = sh(
                    script: "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${env.PAX_SERVER_PORT} \${USERNAME}@${env.PAX_SERVER_HOST} \"cat ${remoteWorkspaceFullPath}/${TEST_ASCII_FILE}\"",
                    returnStdout: true
                ).trim()
                if (textFileContent != TEST_ASCII_CONTENT) {
                    error "Test ASCII file doesn't not contain \"${TEST_ASCII_CONTENT}\": ${textFileContent}"
                }
            }
        } catch (e) {
            throw e
        } finally {
            withCredentials([
                usernamePassword(
                    credentialsId    : env.PAX_SERVER_CREDENTIAL,
                    passwordVariable : 'PASSWORD',
                    usernameVariable : 'USERNAME'
                )
            ]) {
                // delete remote workspace
                sh "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${env.PAX_SERVER_PORT} \${USERNAME}@${env.PAX_SERVER_HOST} \"rm -fr ${remoteWorkspaceFullPath}*\""
            }
        }

        echo "[PAX_PACKAGE_TEST] unpack successfully"
    }
}
