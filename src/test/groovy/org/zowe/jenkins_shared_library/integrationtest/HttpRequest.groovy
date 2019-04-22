/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.integrationtest

import groovy.json.JsonSlurper
import java.io.*
import java.net.*

/**
 * Class to handle HTTP requests
 */
class HttpRequest {
    public static String getText(String url) {
        URL website = new URL(url)
        URLConnection connection = website.openConnection()
        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                    connection.getInputStream()))

        StringBuilder response = new StringBuilder()
        String inputLine

        while ((inputLine = reader.readLine()) != null) {
            response.append(inputLine)
        }

        reader.close()

        return response.toString()
    }

    public static def getJson(String url) {
        // read url content
        String txt = getText(url)

        // parse json
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(txt)

        return object
    }
}
