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

import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils

/**
 * Create PAX file on the folder provided
 *
 * <p>The local work place folder should have these sub-folder(s) or files:</p>
 * <ul>
 * <li><strong>content</strong> folder which holds all the required files/contents</li>
 * <li><strong>ascii</strong> folder is optional, which holds all plain text files will be converted to IBM-1047 encoding</li>
 * <li><strong>prepare-packaging.sh</strong> is the script to prepare workspace. <strong>This script will run in local workspace environment.</strong></li>
 * <li><strong>pre-packaging.sh</strong> is the pre-hook which will run on PAX server before packaging</li>
 * <li><strong>post-packaging.sh</strong> is the post-hook which will run on PAX server after packaging</li>
 * </ul>
 *
 * <p>If the process is successfully, a PAX file (named followed "filename" argument of {@link #pack(Map)}
 * will be placed in the {@link #localWorkspace}.</p>
 */
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
     * @param   job            job identifier
     * @param   filename       package file name will be created
     * @param   environments   environment variables
     * @param   paxOptions     pax write command options
     * @return                 pax package created
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

        def env = this.steps.env
        this.steps.echo "env=${env}"
        def processUid = "${args['job']}-${Utils.getTimestamp()}"
        def remoteWorkspaceFullPath = "${remoteWorkspace}/${processUid}"
        def packageTar = "${processUid}.tar"
        def packageScriptFile = "${processUid}.sh"
        def packageScriptContent = """#!/bin/sh -e
set -x

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
    exit 1
  fi
  rm "${remoteWorkspace}/${packageTar}"
else
  echo "${func}[ERROR] tar ${remoteWorkspace}/${packageTar} file doesn't exist"
  exit 1
fi

# do we have ascii.tar?
if [ -f "${remoteWorkspaceFullPath}/${PATH_ASCII}.tar" ]; then
  echo "${func} extracting ${remoteWorkspaceFullPath}/${PATH_ASCII}.tar ..."
  cd "${remoteWorkspaceFullPath}"
  pax -r -x tar -o to=IBM-1047 -f "${PATH_ASCII}.tar"
  # copy to target folder
  cp -R ${PATH_ASCII}/. ${PATH_CONTENT}
  # remove ascii files
  rm "${PATH_ASCII}.tar"
  rm -fr "${PATH_ASCII}"
fi

# run pre hook
if [ -f "${HOOK_PRE_PACKAGING}" ]; then
  echo "${func} running pre hook ..."
  cd "${remoteWorkspaceFullPath}"
  iconv -f ISO8859-1 -t IBM-1047 ${HOOK_PRE_PACKAGING} > ${HOOK_PRE_PACKAGING}.new
  mv ${HOOK_PRE_PACKAGING}.new ${HOOK_PRE_PACKAGING}
  chmod +x ${HOOK_PRE_PACKAGING}
  echo "${func} launch: ${environmentText} ./${HOOK_PRE_PACKAGING}"
  ${environmentText} ./${HOOK_PRE_PACKAGING}
  if [ \$? -ne 0 ]; then
    exit 1
  fi
fi

# list working folder
cd ${remoteWorkspaceFullPath}
echo "${func} content of ${remoteWorkspaceFullPath} starts >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
ls -REal
echo "${func} content of ${remoteWorkspaceFullPath} ends   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"

# create PAX file
if [ -d "${remoteWorkspaceFullPath}/${PATH_CONTENT}" ]; then
  echo "${func} creating package ..."
  cd "${remoteWorkspaceFullPath}/${PATH_CONTENT}"
  pax -w -f "${remoteWorkspaceFullPath}/${args['filename']}" ${args['paxOptions']} *
  if [ \$? -ne 0 ]; then
    exit 1
  fi
  cd "${remoteWorkspaceFullPath}"
else
  echo "${func}[ERROR] folder ${remoteWorkspaceFullPath}/${PATH_CONTENT} doesn't exist"
  exit 1
fi

# run post hook
if [ -f "${HOOK_POST_PACKAGING}" ]; then
  echo "${func} running post hook ..."
  cd "${remoteWorkspaceFullPath}"
  iconv -f ISO8859-1 -t IBM-1047 ${HOOK_POST_PACKAGING} > ${HOOK_POST_PACKAGING}.new
  mv ${HOOK_POST_PACKAGING}.new ${HOOK_POST_PACKAGING}
  chmod +x ${HOOK_POST_PACKAGING}
  echo "${func} launch: ${environmentText} ./${HOOK_POST_PACKAGING}"
  ${environmentText} ./${HOOK_POST_PACKAGING}
  if [ \$? -ne 0 ]; then
    exit 1
  fi
fi

if [ -f "${remoteWorkspaceFullPath}/${args['filename']}" ]; then
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
                this.steps.sh "\"${this.localWorkspace}/${HOOK_PREPARE_WORKSPACE}\""
            }
            this.steps.sh "echo \"${func} packaging contents:\" && find ${this.localWorkspace}/${PATH_ASCII} -print"
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

        this.steps.lock("packaging-server-${this.sshHost}") {
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
                    this.steps.sh """SSHPASS=\${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${this.sshPort} -b - \${USERNAME}@${this.sshHost} << EOF
get ${remoteWorkspaceFullPath}/${args['filename']} ${this.localWorkspace}
EOF"""
                } catch (ex1) {
                    // throw error
                    throw new PackageException("Pack Pax package failed: ${ex1}")
                } finally {
                    try {
                        // always clean up temporary files/folders
                        this.steps.echo "${func} cleaning up remote workspace..."
                        this.steps.sh "SSHPASS=\${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${this.sshPort} \${USERNAME}@${this.sshHost} \"rm -fr ${remoteWorkspaceFullPath}*\""
                    } catch (ex2) {
                        // ignore errors for cleaning up
                    }
                }
            } // end withCredentials
        } // end lock

        return "${this.localWorkspace}/${args['filename']}"
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
pax -rf ${exactFilename} ${args['paxOptions']}
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
