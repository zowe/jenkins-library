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
 * Get build information from Artifactory by API call
 *
 * @param  artifactoryUrl        Artifactory Service URL
 * @param  artifactoryCredential Artifactory Service Credentials
 * @param  buildName             Build Name to check
 * @param  buildNumber           Build Number to check
 * @param  info='.buildInfo.vcsRevision'  Information to fetch
 * @return                       the value of "info"
 */
def call(String artifactoryUrl, String artifactoryCredential,
  String buildName, String buildNumber, String info='.buildInfo.vcsRevision') {
  def func = "[getArtifactoryBuildInfoByAPI]"
  def result = null

  // FIXME: this could be risky if build name including non-ASCII characters
  def encodedBuildName = buildName.replace(' ', '%20')

  withCredentials([usernamePassword(
    credentialsId: artifactoryCredential,
    passwordVariable: 'PASSWORD',
    usernameVariable: 'USERNAME'
  )]) {
    result = sh(
      script: "curl -u \"${USERNAME}:${PASSWORD}\" -sS \"${artifactoryUrl}/api/build/${encodedBuildName}/${buildNumber}\" | jq -r \"${info}\"",
      returnStdout: true
    ).trim()
  }

  echo "${func} ${info} = ${result}"
  return result
}
