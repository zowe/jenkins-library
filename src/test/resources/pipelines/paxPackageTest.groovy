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
WHICH_BASH=\$(which bash)
cp \$WHICH_BASH ${localWorkspace}/${pathContent}/

echo "[${hookPrepareWorkspace}] prepare a text file ..."
echo "this should be human readable" > ${localWorkspace}/${pathAscii}/test-ascii.txt

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
        pax.pack(TEST_JOB_NAME, "${TEST_JOB_NAME}.pax", ["${TEST_ENV_VAR_NAME}": TEST_ENV_VAR_VALUE])

        if (!fileExists("${localWorkspace}/${TEST_JOB_NAME}.pax")) {
            error 'Failed to find the expected package'
        }

        echo "[PAX_PACKAGE_TEST] package successfully"
    }
}
