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
 * Tag a github repository
 *
 * @param  repository       github repository, for example 'zowe/zowe-install-packaging'
 * @param  revision         github commit hash
 * @param  tag              tag name
 * @param  crendential      github crendential
 * @param  username=''      github user.name
 * @param  email=''         github user.email
 */
def call(String repository, String revision, String tag,
  String crendential, String username='', String email='') {
  def func = "[tagGithubRepository]"
  def tempFolder = '.tag-github-repository-temp'

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
    // tag repository
    sh """
    mkdir ${tempFolder}
    cd ${tempFolder}
    git init
    git remote add origin https://github.com/${repository}.git
    git fetch origin
    git checkout ${revision}
    git tag ${tag}
    git push --tags 'https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${repository}.git'
    """
  }

  echo "${func} all done"
}
