/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2019
 */

/**
 * Create PAX file on the folder provided
 *
 * The work place folder should have these sub-folder or files
 *
 * - "content" folder which holds all the required files/contents
 * - "pre-packaging.sh" is the pre-hook which will run on PAX server before packaging
 * - "post-pacakging.sh" is the post-hook which will run on PAX server after packaging
 *
 * If the process is successfully, a file named as "paxFileName" will be placed in
 * the "workspace".
 *
 * @param  jobId                The job ID which will be used as temp folder/file name
 * @param  paxFileName          PAX file name will be created
 * @param  serverIP             The server IP which we use to create PAX file
 * @param  serverPort           The server port which we use to create PAX file
 * @param  serverCredential           The server username/password credential
 * @param  workspace='./pax-workspace  The folder holds the pax contents
 * @param  serverWorkplaceRoot='/tmp'  In which folder the pax packaging will work in
 * @param  paxWriteOptions=''          Extra pax -w command options
 * @param  environments=[:]     Extra environment variables when runing scripts
 */
def call(String jobId, String paxFileName,
    String serverIP, String serverPort, String serverCredential,
    String workspace='./pax-workspace', String serverWorkplaceRoot='/tmp',
    String paxWriteOptions='', Map environments=[:]) {
  def func = "[CreatePax]"
  def ts = sh(script: "date +%Y%m%d%H%M%S", returnStdout: true).trim()
  def branch = env.BRANCH_NAME
  if (branch.startsWith('origin/')) {
    branch = branch.substring(7)
  }
  branch = branch.replaceAll(/[^a-zA-Z0-9]/, '-').replaceAll(/[\-]+/, '-').toLowerCase()
  def processUid = "${jobId}-${branch}-${ts}"
  def serverWorkplace = "${serverWorkplaceRoot}/${processUid}"
  def environmentText = ""
  try {
    environments.each { envVar, envVal ->
      environmentText += "${envVar}=${envVal} "
    }
    echo "${func} pre-defined environments:"
    echo environmentText
  } catch (err) {
    // ignore errors
    echo "${func}[error] in preparing environments:"
    echo environments
    echo err
  }
  def packageTar = "${processUid}.tar"
  def packageScriptFile = "${processUid}.sh"
  def packageScriptContent = """
#!/bin/sh -e
set -x

if [ -z "${serverWorkplaceRoot}" ]; then
  echo "${func} serverWorkplaceRoot is not set"
  exit 1
fi
if [ -z "${jobId}" ]; then
  echo "${func} jobId is not set"
  exit 1
fi

echo "${func} working in ${serverWorkplace} ..."
mkdir -p "${serverWorkplace}"
cd "${serverWorkplace}"

# extract tar file
if [ -f "${serverWorkplaceRoot}/${packageTar}" ]; then
  echo "${func} extracting ${serverWorkplaceRoot}/${packageTar} to ${serverWorkplace} ..."
  pax -r -x tar -f "${serverWorkplaceRoot}/${packageTar}"
  if [ \$? -ne 0 ]; then
    exit 1
  fi
  rm "${serverWorkplaceRoot}/${packageTar}"
  echo "${func} tar ${packageTar} extracted ..."
  ls -la
else
  echo "${func} tar ${serverWorkplaceRoot}/${packageTar} file doesn't exist"
  exit 1
fi

# run pre hook
if [ -f "pre-packaging.sh" ]; then
  echo "${func} running pre hook ..."
  cd "${serverWorkplace}"
  iconv -f ISO8859-1 -t IBM-1047 pre-packaging.sh > pre-packaging.sh.new
  mv pre-packaging.sh.new pre-packaging.sh
  chmod +x pre-packaging.sh
  echo "${func} launch: ${environmentText} ./pre-packaging.sh"
  ${environmentText} ./pre-packaging.sh
  if [ \$? -ne 0 ]; then
    exit 1
  fi
fi

# create PAX file
if [ -d "${serverWorkplace}/content" ]; then
  echo "${func} creating package ..."
  cd "${serverWorkplace}/content"
  pax -w -f "${serverWorkplace}/${paxFileName}" ${paxWriteOptions} *
  if [ \$? -ne 0 ]; then
    exit 1
  fi
  cd "${serverWorkplace}"
else
  echo "${func} folder ${serverWorkplace}/content doesn't exist"
  exit 1
fi

# run post hook
if [ -f "post-packaging.sh" ]; then
  echo "${func} running post hook ..."
  cd "${serverWorkplace}"
  iconv -f ISO8859-1 -t IBM-1047 post-packaging.sh > post-packaging.sh.new
  mv post-packaging.sh.new post-packaging.sh
  chmod +x post-packaging.sh
  echo "${func} launch: ${environmentText} ./post-packaging.sh"
  ${environmentText} ./post-packaging.sh
  if [ \$? -ne 0 ]; then
    exit 1
  fi
fi

# list working folder
cd ${serverWorkplaceRoot}
echo "${func} temporary content of ${serverWorkplaceRoot}/${jobId}-* ..."
ls -la ${jobId}-*

if [ -f "${serverWorkplace}/${paxFileName}" ]; then
  echo "${func} done"
  exit 0
else
  echo "${func} failed to create PAX file ${serverWorkplace}/${paxFileName}, exit."
  exit 1
fi
"""

  // tar the whole workspace folder
  sh "tar -c -f ${packageTar} -C ${workspace} ."
  writeFile file: packageScriptFile, text: packageScriptContent

  lock("packaging-server-${serverIP}") {
    withCredentials([usernamePassword(credentialsId: serverCredential, passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
      def failure
      try {
        // send to pax server
        sh """SSHPASS=${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${serverPort} -b - ${USERNAME}@${serverIP} << EOF
put ${packageTar} ${serverWorkplaceRoot}
put ${packageScriptFile} ${serverWorkplaceRoot}
EOF"""
        // extract tar file, run pre/post hooks and create pax file
        sh """SSHPASS=${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no -p ${serverPort} ${USERNAME}@${serverIP} << EOF
iconv -f ISO8859-1 -t IBM-1047 ${serverWorkplaceRoot}/${packageScriptFile} > ${serverWorkplaceRoot}/${packageScriptFile}.new
mv ${serverWorkplaceRoot}/${packageScriptFile}.new ${serverWorkplaceRoot}/${packageScriptFile}
chmod +x ${serverWorkplaceRoot}/${packageScriptFile}
. ${serverWorkplaceRoot}/${packageScriptFile}
EOF"""
        // copy back pax file
        sh """SSHPASS=${PASSWORD} sshpass -e sftp -o BatchMode=no -o StrictHostKeyChecking=no -P ${serverPort} -b - ${USERNAME}@${serverIP} << EOF
get ${serverWorkplace}/${paxFileName} ${workspace}
EOF"""
        successful = true
      } catch (ex1) {
        // display errors
        echo "${func}[error] in packaging: ${ex1}"
        failure = ex1
      }

      try {
        // clean up temporary files/folders
        echo "${func} cleaning up ..."
        sh "SSHPASS=${PASSWORD} sshpass -e ssh -tt -o StrictHostKeyChecking=no ${USERNAME}@${serverIP} \"rm -fr ${serverWorkplaceRoot}/${jobId}-${branch}-*\""
      } catch (ex2) {
        // ignore errors for cleaning up
      }

      if (failure) {
        throw failure
      }
    }
  }
}
