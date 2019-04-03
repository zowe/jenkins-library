/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library

import java.net.URLEncoder
import java.time.Instant
import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import org.apache.commons.lang3.StringEscapeUtils

/**
 * Various static methods which doesn't have a class.
 *
 * NOTICE: any method which getting complicated should be extracted as an independent Class.
 */
class Utils {
    /**
     * Read resource file
     * @param  path        path to the resource file
     * @return             the file content
     * @throws IOException if failed to read the file
     */
    static String loadResource(String path) throws IOException {
        return new File(path).getText('UTF-8');
    }

    /**
     * Get current timestamp in a format of YYYYMMDDHHMMSSMMM.
     *
     * The result only includes numbers.
     *
     * @return  timestamp string
     */
    static String getTimestamp() {
        return Instant.now().toString().replaceAll(/[^0-9]/, '')
    }

    /**
     * Convert a map to URL query string
     * @param  params     map to hold the URL parameters
     * @return            URL query string
     */
    static String getUriQueryString(Map params = [:]) {
        // convert params to querystring
        return params.collect { k, v -> k + '=' + URLEncoder.encode(v, 'UTF-8') }.join('&')
    }

    /**
     * Escape special characters in text to make it safe to put in XML
     * @param  params     text to escape
     * @return            escaped text
     */
    static String escapeXml(String text) {
        return StringEscapeUtils.escapeXml11(text)
    }

    /**
     * Get logger
     * @param  name    name of the logger
     * @return         a Logger instance
     */
    static Logger getLogger(String name = '') {
        // reformat the logging
        System.setProperty('java.util.logging.SimpleFormatter.format',
              '[%1$tF %1$tT] [%4$-7s] %5$s %n');

        // get configured logging level
        String logLevel = System.getProperty("logLevel")
        Level level = logLevel ? Level.parse(logLevel) : Constants.DEFAULT_LOGGING_LEVEL

        // init logger
        Logger logger = Logger.getLogger(name)
        logger.setUseParentHandlers(false);
        logger.setLevel(level);

        // remove existing console handler
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            if (handler.getClass() == ConsoleHandler.class) {
                logger.removeHandler(handler);
            }
        }

        // init console handler and attach to logger
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        logger.addHandler(consoleHandler);

        return logger
    }
}
