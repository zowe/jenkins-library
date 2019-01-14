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
 * Promote an artifact to new path
 *
 * Note: the CLI authentication should have been initialized
 *
 * @param  sourceArtifactInfo    Artifact information returned by getArtifactInfoByCLI
 * @param  targetFilePath        Target artifact path
 * @param  targetFileName=''     Target artifact name. If it's empty, will use the original file name
 * @return                       full path of target artifact
 */
def call(Map sourceArtifactInfo,
  String targetFilePath, String targetFileName='') {
  def func = "[promoteArtifact]"

  def buildTimestamp = sourceArtifactInfo['build.timestamp']
  def buildName = sourceArtifactInfo['build.name']
  def buildNumber = sourceArtifactInfo['build.number']

  // extract file name if not provided
  if (!targetFileName) {
    def sourceFilenameTrunks = sourceArtifactInfo['path'].split('/')
    if (sourceFilenameTrunks.size() < 1) {
      error "Invalid artifact: ${sourceArtifactInfo['path']}"
    }
    targetFileName = sourceFilenameTrunks[-1]
  }

  def targetFullPath = "${targetFilePath}/${targetFileName}"

  echo "${func} build \"${buildName}/${buildNumber}\":"
  echo "${func} - build timestamp : ${buildTimestamp}"
  echo "${func} - source artifact : ${sourceArtifactInfo['path']}"
  echo "${func} - target artifact : ${targetFullPath}"

  // promote (copy) artifact
  echo "${func} promoting \"${sourceArtifactInfo['path']}\" to \"${targetFullPath}\""
  def promoteResult = sh(
    script: "jfrog rt copy --flat \"${sourceArtifactInfo.path}\" \"${targetFullPath}\"",
    returnStdout: true
  ).trim()
  echo "${func} promote result: ${promoteResult}"
  def promoteResultObject = readJSON(text: promoteResult)
  if (promoteResultObject['status'] != 'success' ||
      promoteResultObject['totals']['success'] != 1 || promoteResultObject['totals']['failure'] != 0) {
    error "${func} failed on verifying promote result"
  }

  // update file property
  def props = []
  def currentBuildName = env.JOB_NAME.replace('/', ' :: ')
  props << "build.name=${currentBuildName}"
  props << "build.number=${env.BUILD_NUMBER}"
  props << "build.parentName=${buildName}"
  props << "build.parentNumber=${buildNumber}"
  props << "build.timestamp=${buildTimestamp}"
  echo "${func} updating artifact properties:"
  echo props.join("\n")
  def setPropsResult = sh(
    script: "jfrog rt set-props \"${targetFullPath}\" \"" + props.join(';') + "\"",
    returnStdout: true
  ).trim()
  echo "${func} artifact set-props result: ${setPropsResult}"
  def setPropsResultObject = readJSON(text: setPropsResult)
  if (setPropsResultObject['status'] != 'success' ||
      setPropsResultObject['totals']['success'] != 1 || setPropsResultObject['totals']['failure'] != 0) {
    error "${func} failed on verifying set-props result"
  }

  echo "${func} all done"
  return targetFullPath
}
