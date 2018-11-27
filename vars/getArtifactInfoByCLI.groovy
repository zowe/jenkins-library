/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018
 */

/**
 * Get build information from Artifactory by CLI command
 *
 * Note: the CLI authentication should have been initialized
 *
 * @param  artifactPath          Artifact path
 * @param  buildName             Build Name to check from
 * @param  buildNumber           Build Number to check from
 */
def call(String artifactPath, String buildName='', String buildNumber='') {
  def func = "[getArtifactInfoByCLI]"
  def result = [:]

  def artifactorySearch = ""
  if (buildName) {
    // limit to build
    if (buildNumber) {
      artifactorySearch = "--build=\"${buildName}/${buildNumber}\""
    } else {
      artifactorySearch = "--build=\"${buildName}\""
    }
  }

  def artifactsInfoText = sh(
    script: "jfrog rt search ${artifactorySearch} \"${artifactPath}\"",
    returnStdout: true
  ).trim()
  echo "${func} raw search result:"
  echo artifactsInfoText
  /**
   * Example result:
   *
   * [
   *   {
   *     "path": "libs-snapshot-local/com/project/zowe/0.9.0-SNAPSHOT/zowe-0.9.0-20180918.163158-38.pax",
   *     "props": {
   *       "build.name": "zowe-install-packaging :: master",
   *       "build.number": "38",
   *       "build.parentName": "zlux",
   *       "build.parentNumber": "570",
   *       "build.timestamp": "1537287202277"
   *     }
   *   }
   * ]
   */
  def artifactsInfo = readJSON text: artifactsInfoText

  // validate result size
  def resultSize = artifactsInfo.size()
  if (resultSize < 1) {
    if (buildNumber) {
      error "${func} cannot find file \"${artifactPath}\" from build \"${buildName}/${buildNumber}\""
    } else {
      error "${func} cannot find file \"${artifactPath}\""
    }
  }
  if (resultSize > 1) {
    if (buildNumber) {
      error "${func} found ${resultSize} files of \"${artifactPath}\" from build \"${buildName}/${buildNumber}\""
    } else {
      error "${func} found ${resultSize} files of \"${artifactPath}\""
    }
  }

  // fetch the first artifact
  def artifactInfo = artifactsInfo.first()

  // validate build info
  if (!artifactInfo || !artifactInfo.path) {
    error "${func} failed to find artifact information."
  }
  result['path'] = artifactInfo.path

  // append build information
  ['build.timestamp', 'build.name', 'build.number', 'build.parentName', 'build.parentNumber'].each { key ->
    def val = artifactInfo.props.get(key)
    // think this should be a bug
    // readJSON returns val as net.sf.json.JSONArray
    // this step is a workaround
    if (val.getClass().toString().endsWith('JSONArray')) {
      val = val.get(0)
    }
    result[key] = val
  }

  echo "${func} renderred result: ${result.inspect()}"
  return result
}
