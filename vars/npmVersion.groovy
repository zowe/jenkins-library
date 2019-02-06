/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

/**
 * Increase NPM version of a github project
 *
 * @param  repository       github repository, for example 'zowe/zowe-install-packaging'
 * @param  branch           github branch to checkout and bump version
 * @param  version          npm version bump strategy
 * @param  crendential      github crendential
 * @param  username=''      github user.name
 * @param  email=''         github user.email
 */
def call(String repository, String branch, String version,
  String crendential, String username='', String email='') {
  def func = "[npmVersion]"
  def tempFolder = '.npm-project-version-bump-temp'

  // configure git global var
  if (username) {
    sh "git config --global user.name \"${username}\""
  }
  if (email) {
    sh "git config --global user.email \"${email}\""
  }

  withCredentials([usernamePassword(
    credentialsId: crendential,
    passwordVariable: 'GIT_PASSWORD',
    usernameVariable: 'GIT_USERNAME'
  )]) {
    // checkout repository, bump version and push back
    sh """
    git clone --depth 1 https://github.com/${repository}.git -b "${branch}" "${tempFolder}"
    cd ${tempFolder}
    npm version ${version}
    git push 'https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${repository}.git'
    cd ..
    rm -fr ${tempFolder}
    """
  }

  echo "${func} all done"
}
