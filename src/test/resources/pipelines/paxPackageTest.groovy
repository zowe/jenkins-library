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
        sh "mkdir -p ${localWorkspace}/${pax.PATH_CONTENT}"
        sh "mkdir -p ${localWorkspace}/${pax.PATH_ASCII}"
        // write prepare hook
        writeFile file: "${localWorkspace}/${pax.HOOK_PREPARE_PACKAGING}", text: """
echo "[${pax.HOOK_PREPARE_PACKAGING}] started ..."
echo "[${pax.HOOK_PREPARE_PACKAGING}] pwd=\$(pwd)"

echo "[${pax.HOOK_PREPARE_PACKAGING}] prepare a binary file ..."
WHICH_BASH=\$(which bash)
cp \$WHICH_BASH ${localWorkspace}/${pax.PATH_CONTENT}/

echo "[${pax.HOOK_PREPARE_PACKAGING}] prepare a text file ..."
echo "this should be human readable" > ${localWorkspace}/${pax.PATH_ASCII}/test-ascii.txt

echo "[${pax.HOOK_PREPARE_PACKAGING}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"

echo "[${pax.HOOK_PREPARE_PACKAGING}] ended."
"""

        // write pre hook
        writeFile file: "${localWorkspace}/${pax.HOOK_PRE_PACKAGING}", text: """
echo "[${pax.HOOK_PRE_PACKAGING}] started ..."
echo "[${pax.HOOK_PRE_PACKAGING}] pwd=\$(pwd)"
echo "[${pax.HOOK_PRE_PACKAGING}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"
echo "[${pax.HOOK_PRE_PACKAGING}] ended."
"""

        // write post hook
        writeFile file: "${localWorkspace}/${pax.HOOK_POST_PACKAGING}", text: """
echo "[${pax.HOOK_POST_PACKAGING}] started ..."
echo "[${pax.HOOK_POST_PACKAGING}] pwd=\$(pwd)"
echo "[${pax.HOOK_POST_PACKAGING}] ${TEST_ENV_VAR_NAME}=\${${TEST_ENV_VAR_NAME}}"
echo "[${pax.HOOK_POST_PACKAGING}] ended."
"""

        echo "[PAX_PACKAGE_TEST] init successfully"
    }

    /**
     * Should be able to create a PAX package
     */
    stage('package') {
        pax.package(TEST_JOB_NAME, "${TEST_JOB_NAME}.pax", ["${TEST_ENV_VAR_NAME}": TEST_ENV_VAR_VALUE])

        if (!fileExists("${localWorkspace}/${TEST_JOB_NAME}.pax")) {
            error 'Failed to find the expected package'
        }

        echo "[PAX_PACKAGE_TEST] package successfully"
    }
}
