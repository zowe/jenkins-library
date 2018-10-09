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
 * Login to private NPM registry
 *
 * NOTE: the old ~/.npmrc will be backup as ~/.npmrc-bak
 *
 * @param  npmRegistry      NPM registry url
 * @param  npmCredential    NPM registry API token
 * @param  npmUser          NPM user email address
 * @return                  npm user name
 */
def call(String npmRegistry, String npmCredential, String npmUser) {
  def npmFile = '~/.npmrc'

  // create if it's not existed
  sh "touch ${npmFile}"
  // backup current .npmrc
  sh "mv ${npmFile} ${npmFile}-bak"

  // update auth in .npmrc
  sh "npm config set registry ${npmRegistry}"
  withCredentials([string(credentialsId: npmCredential, variable: 'TOKEN')]) {
    sh "npm config set _auth ${TOKEN}"
  }
  sh "npm config set email ${npmUser}"
  sh "npm config set always-auth true"

  // verify login information
  def whoami = sh(script: "npm whoami", returnStdout: true).trim()
  echo "npm user: ${whoami}"

  return whoami
}
