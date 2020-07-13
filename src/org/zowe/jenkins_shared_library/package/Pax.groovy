/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.package

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils

/**
 * Create PAX file on the folder provided
 *
 * <p>The local work place folder should have these sub-folder(s) or files:</p>
 * <ul>
 * <li><strong>content</strong> folder which holds all the required files/contents</li>
 * <li><strong>ascii</strong> folder is optional, which holds all plain text files will be converted to IBM-1047 encoding</li>
 * <li><strong>prepare-workspace.sh</strong> is the script to prepare workspace. <strong>This script will run in local workspace environment.</strong></li>
 * <li><strong>pre-packaging.sh</strong> is the pre-hook which will run on PAX server before packaging</li>
 * <li><strong>post-packaging.sh</strong> is the post-hook which will run on PAX server after packaging</li>
 * <li><strong>catchall-packaging.sh</strong> is the catch-all-hook which will run on PAX server (after post-packaging) no matter the packaging process succeeds or exits with error</li>
 * </ul>
 *
 * <p>If the process is successfully, a PAX file (named followed "filename" argument of {@link #pack(Map)}
 * will be placed in the {@link #localWorkspace}.</p>
 */
@Log
class Pax {
    /**
     * Constant of default local workspace folder.
     *
     * @Default {@code ".pax"}
     */
    static final String DEFAULT_LOCAL_WORKSPACE = './.pax'

    /**
     * Constant of default remote workspace folder.
     *
     * @Default {@code "/tmp"}
     */
    static final String DEFAULT_REMOTE_WORKSPACE = '/tmp'

    /**
     * Constant of local content folder name
     *
     * @Default {@code "content"}
     */
    static final String PATH_CONTENT = 'content'

    /**
     * Constant of local ascii folder name
     *
     * @Default {@code "ascii"}
     */
    static final String PATH_ASCII = 'ascii'

    /**
     * Constant of prepare-packaging hook name
     *
     * @Note This hook script runs on local workspace.
     *
     * @Default {@code "prepare-workspace.sh"}
     */
    static final String HOOK_PREPARE_WORKSPACE = 'prepare-workspace.sh'

    /**
     * Constant of pre-packaging hook
     *
     * @Note This hook script runs on remote workspace.
     *
     * @Default {@code "pre-packaging.sh"}
     */
    static final String HOOK_PRE_PACKAGING = 'pre-packaging.sh'

    /**
     * Constant of post-packaging hook
     *
     * @Note This hook script runs on remote workspace.
     *
     * @Default {@code "post-packaging.sh"}
     */
    static final String HOOK_POST_PACKAGING = 'post-packaging.sh'

    /**
     * Constant of catchall-packaging hook
     *
     * @Note This hook script runs on remote workspace.
     *
     * @Default {@code "catchall-packaging.sh"}
     */
    static final String HOOK_CATCHALL_PACKAGING = 'catchall-packaging.sh'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * Local workspace folder name
     */
    String localWorkspace

    /**
     * Remote workspace folder name
     */
    String remoteWorkspace

    /**
     * SSH server to run pax
     */
    String sshHost

    /**
     * SSH server port
     *
     * @Default {@code 22}
     */
    String sshPort = '22'

    /**
     * SSH server credential ID
     *
     * <p>The content of token could be base64 encoded "username:password".</p>
     */
    String sshCredential

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pax = new Pax(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Pax(steps) {
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize pax packaging properties
     *
     * @param   localWorkspace       workspace folder on local. Default value is {@link #DEFAULT_LOCAL_WORKSPACE}.
     * @param   remoteWorkspace      workspace folder on remote (ssh server). Default value is {@link #DEFAULT_REMOTE_WORKSPACE}.
     * @param   sshHost              hostname/ip of packaging server
     * @param   sshPort              ssh port of packaging server
     * @param   sshCredential        SSH credential of packaging server
     */
    void init(Map args = [:]) {
        if (args['localWorkspace']) {
            this.localWorkspace = args['localWorkspace']
        }
        if (!this.localWorkspace) {
            this.localWorkspace = DEFAULT_LOCAL_WORKSPACE
        }
        if (args['remoteWorkspace']) {
            this.remoteWorkspace = args['remoteWorkspace']
        }
        if (!this.remoteWorkspace) {
            this.remoteWorkspace = DEFAULT_REMOTE_WORKSPACE
        }
        if (args['sshHost']) {
            this.sshHost = args['sshHost']
        }
        if (args['sshPort']) {
            this.sshPort = args['sshPort']
        }
        if (args['sshCredential']) {
            this.sshCredential = args['sshCredential']
        }
    }

    /**
     * Create PAX Package
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   job             job identifier
     * @param   filename        package file name will be created
     * @param   extraFiles      extra artifacts will be generated and should be transferred back
     * @param   environments    environment variables
     * @param   paxOptions      pax write command options
     * @param   compress        if we want to compress the result
     * @param   compressOptions compress command options
     * @param   keepTempFolder   if we want to keep the temporary packaging folder on the remote machine
     * @param   buildDocker     take pax and build docker out of it
     *                           for debugging purpose. Default is false.
     * @return                   pax package created
     */
    String pack(Map args = [:]) throws InvalidArgumentException, PackageException {
        def func = '[Pax.pack]'

        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.sshHost) {
            throw new InvalidArgumentException('sshHost')
        }
        if (!this.sshCredential) {
            throw new InvalidArgumentException('sshCredential')
        }
        if (!args['job']) {
            throw new InvalidArgumentException('job')
        }
        if (!args['filename']) {
            throw new InvalidArgumentException('filename')
        }

        // parse environment argument
        def environmentText = ""
        if (args.containsKey('environments') && args['environments'] instanceof Map) {
            try {
                args['environments'].each { envVar, envVal ->
                    environmentText += "${envVar}=${envVal} "
                }
                this.steps.echo "${func} pre-defined environments: ${environmentText}"
            } catch (err) {
                // FIXME: ignore errors, or throw?
                this.steps.echo "${func}[WARN] failed to prepare environments: ${args['environments']}\n${err}"
            }
        }
        def keepTempFolder = false
        if (args.containsKey('keepTempFolder') && args['keepTempFolder']) {
            keepTempFolder = true
        }
        def buildDocker = false
        if (args.containsKey('buildDocker') && args['buildDocker']) {
            buildDocker = true
        }
        def compressPax = false
        if (args.containsKey('compress') && args['compress']) {
            compressPax = true
        }
        def filePax = args['filename']
        def filePaxZ = args['filename']
        if (compressPax) {
            if (filePax.endsWith('.Z')) {
                filePax = filePax[0..-3]
            } else {
                filePaxZ = filePax + '.Z'
            }
        }
        def extraFiles = []
        if (args.containsKey('extraFiles')) {
            if (args['extraFiles'] instanceof String) {
                if (args['extraFiles']) {
                    extraFiles = args['extraFiles'].split(/,/)
                }
            } else if (args['extraFiles'] instanceof ArrayList || args['extraFiles'] instanceof String[]) {
                if (args['extraFiles'].size() > 0) {
                    extraFiles = args['extraFiles']
                }
            } else if (args['extraFiles']) {
                throw new InvalidArgumentException('extraFiles', "extraFiles with type ${args['extraFiles'].getClass()} is not accepted")
            }
        }

        def env = this.steps.env
        this.steps.echo "env=${env}"
        def processUid = "${args['job']}-${Utils.getTimestamp()}"
        def remoteWorkspaceFullPath = "${remoteWorkspace}/${processUid}"
        def packageTar = "${processUid}.tar"
        def packageScriptFile = "${processUid}.sh"
        def packageScriptContent = """#!/bin/sh -e
set +x

if [ -z "${remoteWorkspace}" ]; then
  echo "${func}[ERROR] remoteWorkspace is not set"
  exit 1
fi
if [ -z "${args['job']}" ]; then
  echo "${func}[ERROR] job id is not set"
  exit 1
fi

echo "${func} working in ${remoteWorkspaceFullPath} ..."
mkdir -p "${remoteWorkspaceFullPath}"
cd "${remoteWorkspaceFullPath}"

# extract tar file
if [ -f "${remoteWorkspace}/${packageTar}" ]; then
  echo "${func} extracting ${remoteWorkspace}/${packageTar} to ${remoteWorkspaceFullPath} ..."
  pax -r -x tar -f "${remoteWorkspace}/${packageTar}"
  if [ \$? -ne 0 ]; then
    echo "${func}[ERROR] failed on untar package"
    exit 1
  fi
  rm "${remoteWorkspace}/${packageTar}"
else
  echo "${func}[ERROR] tar ${remoteWorkspace}/${packageTar} file doesn't exist"
  exit 1
fi

# do we have ascii.tar?
cd "${remoteWorkspaceFullPath}"
if [ -f "${PATH_ASCII}.tar" ]; then
  echo "${func} extracting ${remoteWorkspaceFullPath}/${PATH_ASCII}.tar ..."
  pax -r -x tar -o to=IBM-1047 -f "${PATH_ASCII}.tar"
  # copy to target folder
  cp -R ${PATH_ASCII}/. ${PATH_CONTENT}
  # remove ascii files
  rm "${PATH_ASCII}.tar"
  rm -fr "${PATH_ASCII}"
fi

# run pre hook
cd "${remoteWorkspaceFullPath}"
if [ -f "${HOOK_PRE_PACKAGING}" ]; then
  echo "${func} running pre hook ..."
  iconv -f ISO8859-1 -t IBM-1047 ${HOOK_PRE_PACKAGING} > ${HOOK_PRE_PACKAGING}.new
  mv ${HOOK_PRE_PACKAGING}.new ${HOOK_PRE_PACKAGING}
  chmod +x ${HOOK_PRE_PACKAGING}
  echo "${func} launch: ${environmentText} ./${HOOK_PRE_PACKAGING}"
  ${environmentText} ./${HOOK_PRE_PACKAGING}
  if [ \$? -ne 0 ]; then
    echo "${func}[ERROR] failed on pre hook"
    exit 1
  fi
fi

# list working folder
cd ${remoteWorkspaceFullPath}
echo "${func} content of ${remoteWorkspaceFullPath} starts >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
ls -TREal
echo "${func} content of ${remoteWorkspaceFullPath} ends   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"

# create PAX file
if [ -d "${remoteWorkspaceFullPath}/${PATH_CONTENT}" ]; then
  echo "${func} creating package ..."
  echo "${func}   ${remoteWorkspaceFullPath}/${PATH_CONTENT}\$ pax -w -f ${remoteWorkspaceFullPath}/${filePax} ${args['paxOptions'] ?: ''} *"
  cd "${remoteWorkspaceFullPath}/${PATH_CONTENT}"
  pax -w -f "${remoteWorkspaceFullPath}/${filePax}" ${args['paxOptions'] ?: ''} *
  if [ \$? -ne 0 ]; then
    echo "${func}[ERROR] failed on creating pax file"
    exit 1
  fi
else
  echo "${func}[ERROR] folder ${remoteWorkspaceFullPath}/${PATH_CONTENT} doesn't exist"
  exit 1
fi

# run post hook
cd "${remoteWorkspaceFullPath}"
if [ -f "${HOOK_POST_PACKAGING}" ]; then
  echo "${func} running post hook ..."
  iconv -f ISO8859-1 -t IBM-1047 ${HOOK_POST_PACKAGING} > ${HOOK_POST_PACKAGING}.new
  mv ${HOOK_POST_PACKAGING}.new ${HOOK_POST_PACKAGING}
  chmod +x ${HOOK_POST_PACKAGING}
  echo "${func} launch: ${environmentText} ./${HOOK_POST_PACKAGING}"
  ${environmentText} ./${HOOK_POST_PACKAGING}
  if [ \$? -ne 0 ]; then
    echo "${func}[ERROR] failed on post hook"
    exit 1
  fi
fi

# need to compress?
if [ "${compressPax ? 'YES' : 'NO'}" = "YES" ]; then
  echo "${func} compressing ${remoteWorkspaceFullPath}/${filePax} ..."
  compress ${args['compressOptions'] ?: ''} "${remoteWorkspaceFullPath}/${filePax}"
fi

if [ -f "${remoteWorkspaceFullPath}/${filePax}" ]; then
  echo "${func} done"
  exit 0
elif [ -f "${remoteWorkspaceFullPath}/${filePaxZ}" ]; then
  echo "${func} done"
  exit 0
else
  echo "${func}[ERROR] failed to create PAX file ${remoteWorkspaceFullPath}/${args['filename']}, exit."
  exit 1
fi
"""

        try {
            // run prepare-packaging hook if exists
            if (this.steps.fileExists("${this.localWorkspace}/${HOOK_PREPARE_WORKSPACE}")) {
                this.steps.sh "${environmentText} \"${this.localWorkspace}/${HOOK_PREPARE_WORKSPACE}\""
            }
            this.steps.sh "echo \"${func} packaging contents:\" && find ${this.localWorkspace} -print"
            // tar ascii folder if exists
            if (this.steps.fileExists("${this.localWorkspace}/${PATH_ASCII}")) {
                this.steps.sh """tar -c -f ${this.localWorkspace}/${PATH_ASCII}.tar -C ${this.localWorkspace}/ ${PATH_ASCII}
rm -fr ${this.localWorkspace}/${PATH_ASCII}
"""
            }
            // tar the whole workspace folder
            this.steps.sh "tar -c -f ${packageTar} -C ${this.localWorkspace} ."
            this.steps.writeFile file: packageScriptFile, text: packageScriptContent
        } catch (ex0) {
            // throw error
            throw new PackageException("Failed to prepare packaging workspace: ${ex0}")
        }

        // this.steps.lock("packaging-server-${this.sshHost}") {
            this.steps.withCredentials([
                this.steps.usernamePassword(
                    credentialsId    : this.sshCredential,
                    passwordVariable : 'PASSWORD',
                    usernameVariable : 'USERNAME'
                )
            ]) {
                try {
                    // send to pax server
                    this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${this.sshPort} -b - \${USERNAME}@${this.sshHost} << EOF
put ${packageTar} ${remoteWorkspace}
put ${packageScriptFile} ${remoteWorkspace}
EOF"""
                    // extract tar file, run pre/post hooks and create pax file
                    this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} << EOF
iconv -f ISO8859-1 -t IBM-1047 ${remoteWorkspace}/${packageScriptFile} > ${remoteWorkspace}/${packageScriptFile}.new
mv ${remoteWorkspace}/${packageScriptFile}.new ${remoteWorkspace}/${packageScriptFile}
chmod +x ${remoteWorkspace}/${packageScriptFile}
. ${remoteWorkspace}/${packageScriptFile}
rm ${remoteWorkspace}/${packageScriptFile}
exit 0
EOF"""
                    // copy back pax file
                    String extraGets = ""
                    extraFiles.each {
                        extraGets += "\nget ${remoteWorkspaceFullPath}/${it} ${this.localWorkspace}"
                    }
                    this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${this.sshPort} -b - \${USERNAME}@${this.sshHost} << EOF
get ${remoteWorkspaceFullPath}/${compressPax ? filePaxZ : filePax} ${this.localWorkspace}${extraGets}
EOF"""

                    this.steps.sh """dockerd"""
                    this.steps.sh """docker build https://github.com/1000TurquoisePogs/zowe-dockerfiles.git#s390x:dockerfiles/zowe-release/amd64/zowe-v1-lts --build-arg PAX_FILE=${this.localWorkspace}/${filePax}"""
                } catch (ex1) {
                    // throw error
                    throw new PackageException("Pack Pax package failed: ${ex1}")
                } finally {
                    if (keepTempFolder) {
                        this.steps.echo "${func}[warning] remote workspace will be left as-is without clean-up."
                    } else {
                        try {
                            // run catch-all hooks
                            this.steps.echo "${func} running catch-all hooks..."
                            this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} << EOF
cd "${remoteWorkspaceFullPath}"
if [ -f "${HOOK_CATCHALL_PACKAGING}" ]; then
  echo "${func} running catch-all hook ..."
  iconv -f ISO8859-1 -t IBM-1047 ${HOOK_CATCHALL_PACKAGING} > ${HOOK_CATCHALL_PACKAGING}.new
  mv ${HOOK_CATCHALL_PACKAGING}.new ${HOOK_CATCHALL_PACKAGING}
  chmod +x ${HOOK_CATCHALL_PACKAGING}
  echo "${func} launch: ${environmentText} ./${HOOK_CATCHALL_PACKAGING}"
  ${environmentText} ./${HOOK_CATCHALL_PACKAGING}
  if [ \$? -ne 0 ]; then
    echo "${func}[ERROR] failed on catch-all hook"
    exit 1
  fi
fi
exit 0
EOF"""
                        } catch (ex3) {
                            // ignore errors for cleaning up
                            this.log.finer("${func} running catch-all hooks failed: ${ex2}")
                        }

                        try {
                            // always clean up temporary files/folders
                            this.steps.echo "${func} cleaning up remote workspace..."
                            def resultCleaning = this.steps.sh(
                                script: "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} \"rm -fr ${remoteWorkspaceFullPath}*\"",
                                returnStdout: true
                            )
                            this.log.finer("${func} cleaning up remote workspace returns: ${resultCleaning}")
                        } catch (ex2) {
                            // ignore errors for cleaning up
                            this.log.finer("${func} cleaning up remote workspace failed: ${ex2}")
                        }
                    }
                }
            } // end withCredentials
        // } // end lock

        return "${this.localWorkspace}/${compressPax ? filePaxZ : filePax}"
    } // end package()

    /**
     * Create PAX Package
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @see #pack(Map)
     */
    String pack(String job, String filename, Map environments = [:], String paxOptions = '') {
        this.pack(job: job, filename: filename, environments: environments, paxOptions: paxOptions)
    }

    /**
     * Extract PAX Package to remoteWorkspace
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   filename       package file name will be extracted
     * @param   paxOptions     pax extract command options
     * @return                 pax package content list with "find . -print"
     */
    String unpack(Map args = [:]) throws InvalidArgumentException, PackageException {
        def func = '[Pax.unpack]'

        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.sshHost) {
            throw new InvalidArgumentException('sshHost')
        }
        if (!this.sshCredential) {
            throw new InvalidArgumentException('sshCredential')
        }
        if (!args['filename']) {
            throw new InvalidArgumentException('filename')
        }
        def exactFilename = args['filename'].split('/').last()

        def result = ''

        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId    : this.sshCredential,
                passwordVariable : 'PASSWORD',
                usernameVariable : 'USERNAME'
            )
        ]) {
            try {
                // send to pax server
                this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${this.sshPort} -b - \${USERNAME}@${this.sshHost} << EOF
put ${args['filename']} ${remoteWorkspace}
EOF"""
                // extract tar file, run pre/post hooks and create pax file
                this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} << EOF
cd ${remoteWorkspace}
pax -rf ${exactFilename} ${args['paxOptions'] ?: ''}
exit 0
EOF"""
                // get extracted result
                result = this.steps.sh(
                    script: "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} \"find ${remoteWorkspace} -print\"",
                    returnStdout: true
                ).trim()
            } catch (ex1) {
                // throw error
                throw new PackageException("Unpack Pax package failed: ${ex1}")
            }
        } // end withCredentials

        return result
    }

    /**
     * Extract PAX Package to remoteWorkspace
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @see #unpack(Map)
     */
    String unpack(String filename, String remoteWorkspace, String paxOptions = '') {
        this.unpack(filename: filename, remoteWorkspace: remoteWorkspace, paxOptions: paxOptions)
    }
}
