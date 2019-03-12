/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2019
 */

package org.zowe.artifactory

class ArtifactoryBase {
  def steps

  ArtifactoryBase(steps) {
    this.steps = steps
  }

  String test(String param1, String param2, Integer param3 = null) {
    def ret = """
>>>>>>>>>>>> test started >>>>>>>>>>>
param1 = ${param1}
param2 = ${param2}
param3 = ${param3}
"""

    return ret
  }
}
