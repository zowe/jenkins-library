/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

package org.zowe.jenkins_shared_library.package

import groovy.util.logging.Log
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Constants
import org.zowe.jenkins_shared_library.Utils

/**
 * Sign package
 *
 * <p>This class helps signing or generating hash of a package/file.</p>
 */
@Log
class Signing {
    /**
     * Constant of default hash algorithm.
     *
     * @Default {@code "SHA512"}
     */
    static final String DEFAULT_HASH_ALGORITHM = 'SHA512'

    /**
     * Reference to the groovy pipeline variable.
     */
    def steps

    /**
     * GnuPG Code Signing Key Passphrase Jenkins username/password credential
     */
    String gpgKeyPassPhrase

    /**
     * GnuPG Code Signing Private Key Jenkins Secret file credential
     */
    String gpgPrivateKey

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def signing = new Signing(this)
     * </pre>
     *
     * @param steps    The workflow steps object provided by the Jenkins pipeline
     */
    Signing(steps) {
        // init jenkins instance property
        this.steps = steps
    }

    /**
     * Initialize signing properties
     *
     * @param   gpgKeyPassPhrase     GnuPG Code Signing Key Passphrase Jenkins username/password credential. Default value is {@link org.zowe.jenkins_shared_library.Constants#DEFAULT_GPG_CODE_SIGNING_KEY_PASSPHRASE}.
     * @param   gpgPrivateKey        GnuPG Code Signing Private Key Jenkins Secret file credential. Default value is {@link org.zowe.jenkins_shared_library.Constants#DEFAULT_GPG_CODE_SIGNING_PRIVATE_KEY_FILE}.
     */
    void init(Map args = [:]) {
        if (args['gpgKeyPassPhrase']) {
            this.gpgKeyPassPhrase = args['gpgKeyPassPhrase']
        }
        if (!this.gpgKeyPassPhrase) {
            this.gpgKeyPassPhrase = Constants.DEFAULT_GPG_CODE_SIGNING_KEY_PASSPHRASE
        }
        if (args['gpgPrivateKey']) {
            this.gpgPrivateKey = args['gpgPrivateKey']
        }
        if (!this.gpgPrivateKey) {
            this.gpgPrivateKey = Constants.DEFAULT_GPG_CODE_SIGNING_PRIVATE_KEY_FILE
        }
    }

    /**
     * Check if a GnuPG exists.
     *
     * @param   key          GnuPG key to check
     * @return               exist or not
     */
    Boolean gpgKeyExists(String key) {
        def checkKey = this.steps.sh(
            script: "gpg --list-keys",
            returnStdout: true
        ).trim()
        log.fine("gpg keys list:\n${checkKey}")

        return checkKey.contains(key)
    }

    /**
     * GnuPG sign a package
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   filename        package file will be signed
     * @return                  signed signature file
     */
    String sign(Map args = [:]) throws InvalidArgumentException, PackageException {
        def func = '[Signing.sign]'

        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.gpgKeyPassPhrase) {
            throw new InvalidArgumentException('gpgKeyPassPhrase')
        }
        if (!this.gpgPrivateKey) {
            throw new InvalidArgumentException('gpgPrivateKey')
        }
        if (!args['filename']) {
            throw new InvalidArgumentException('filename')
        }
        if (!this.steps.fileExists(args['filename'])) {
            throw new PackageException("File ${args['filename']} doesn't exists.")
        }
        def signature = "${args['filename']}.asc"

        // imported key if not exist
        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId    : this.gpgKeyPassPhrase,
                passwordVariable : 'JC_KEY_PASSPHRASE',
                usernameVariable : 'JC_KEY_ID'
            ),
            this.steps.file(
                credentialsId    : this.gpgPrivateKey,
                variable         : 'JC_PRIVATE_KEY'
            )
        ]) {
            String signingKey = this.steps.sh(script: "echo \"\${JC_KEY_ID}\"", returnStdout: true).trim()
            // imported key if not exist
            if (!gpgKeyExists(signingKey)) {
                this.steps.echo "${func} importing code signing key ${signingKey} ..."
                this.steps.sh "gpg --allow-secret-key-import --batch --passphrase \"\${JC_KEY_PASSPHRASE}\"  --import \${JC_PRIVATE_KEY}"
                if (!gpgKeyExists(signingKey)) {
                    throw new InvalidArgumentException('gpgKey', "Code signing key ${signingKey} is not imported correctly.")
                }
            }

            if (this.steps.fileExists(signature)) {
                throw new PackageException("Signature file ${signature} already exists.")
            }

            // sign the file
            this.steps.echo "${func} signing ${args['filename']} with key ${signingKey} ..."
            this.steps.sh "echo \"\${JC_KEY_PASSPHRASE}\" | gpg --batch --pinentry-mode loopback --passphrase-fd 0 --local-user ${signingKey} --sign --armor --detach-sig ${args['filename']}"

            if (!this.steps.fileExists(signature)) {
                throw new PackageException("Signature file ${signature} is not created.")
            }
        }

        return signature
    } // end sign()

    /**
     * GnuPG sign a package
     *
     * @see #sign(Map)
     */
    String sign(String filename) {
        return this.sign(filename: filename)
    }

    /**
     * Verify if the signature is good
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   filename        package file will be verified
     * @param   signature       signature file. Optional, default will be filename with .asc extension
     * @return                  valid or not
     */
    Boolean verifySignature(Map args = [:]) throws InvalidArgumentException, PackageException {
        def func = '[Signing.verifySignature]'

        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.gpgKeyPassPhrase) {
            throw new InvalidArgumentException('gpgKeyPassPhrase')
        }
        if (!this.gpgPrivateKey) {
            throw new InvalidArgumentException('gpgPrivateKey')
        }
        if (!args['filename']) {
            throw new InvalidArgumentException('filename')
        }
        if (!this.steps.fileExists(args['filename'])) {
            throw new PackageException("File ${args['filename']} doesn't exists.")
        }
        def signature = "${args['filename']}.asc"
        if (args['signature']) {
            signature = args['signature']
        }
        if (!this.steps.fileExists(signature)) {
            throw new PackageException("Signature file ${signature} doesn't exists.")
        }

        this.steps.withCredentials([
            this.steps.usernamePassword(
                credentialsId    : this.gpgKeyPassPhrase,
                passwordVariable : 'JC_KEY_PASSPHRASE',
                usernameVariable : 'JC_KEY_ID'
            ),
            this.steps.file(
                credentialsId    : this.gpgPrivateKey,
                variable         : 'JC_PRIVATE_KEY'
            )
        ]) {
            String signingKey = this.steps.sh(script: "echo \"\${JC_KEY_ID}\"", returnStdout: true).trim()
            // imported key if not exist
            if (!gpgKeyExists(signingKey)) {
                this.steps.echo "${func} importing code signing key ${signingKey} ..."
                this.steps.sh "gpg --allow-secret-key-import --batch --passphrase \"\${JC_KEY_PASSPHRASE}\"  --import \${JC_PRIVATE_KEY}"
                if (!gpgKeyExists(signingKey)) {
                    throw new InvalidArgumentException('gpgKey', "Code signing key ${signingKey} is not imported correctly.")
                }
            }

            // verify the file
            this.steps.echo "${func} verifying ${args['filename']} ..."
            def tmp = ".tmp-${Utils.getTimestamp()}"
            def result = this.steps.sh(
                script: "gpg --verify ${signature} ${args['filename']} 2>&1 | tee ${tmp} && cat ${tmp} && rm -f ${tmp}",
                returnStdout: true
            ).trim()
            log.fine("gpg verify result:\n${result}")

            if (result.contains("Good signature from")) {
                this.steps.echo "${func} - Valid"
                return true
            } else {
                this.steps.echo "${func} - Invalid"
            }
        }

        return false
    } // end verifySignature

    /**
     * Verify if the signature is good
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @see #verifySignature(Map)
     */
    Boolean verifySignature(String filename) {
        return this.verifySignature(filename: filename)
    }

    /**
     * Generate hash file for a package
     *
     * @Note Use similar parameters defined in {@link #init(Map)} method and with these extra parameters:
     *
     * @param   filename        package file will be signed
     * @param   algo            Algorithm to generate hash. Default is {@link #DEFAULT_HASH_ALGORITHM}.
     * @return                  hash file
     */
    String hash(Map args = [:]) throws InvalidArgumentException, PackageException {
        def func = '[Signing.hash]'

        // init with arguments
           if (args.size() > 0) {
            this.init(args)
        }
        // validate arguments
        if (!this.gpgKeyPassPhrase) {
            throw new InvalidArgumentException('gpgKeyPassPhrase')
        }
        if (!this.gpgPrivateKey) {
            throw new InvalidArgumentException('gpgPrivateKey')
        }
        if (!args['filename']) {
            throw new InvalidArgumentException('filename')
        }
        if (!this.steps.fileExists(args['filename'])) {
            throw new PackageException("File ${args['filename']} doesn't exists.")
        }
        def algo = DEFAULT_HASH_ALGORITHM
        if (args['algo']) {
            algo = args['algo']
        }
        def hashFile = "${args['filename']}.${algo.toLowerCase()}"
        if (this.steps.fileExists(hashFile)) {
            this.steps.echo "${func}[Warning] Hash file ${hashFile} already exists, will be overwritten."
        }

        // generate hash
        this.steps.echo "${func} generate hash for ${args['filename']} ..."
        this.steps.sh [
            "F_PATH=\$(dirname \"${args['filename']}\")",
            "F_NAME=\$(basename \"${args['filename']}\")",
            "cd \$F_PATH",
            "gpg --print-md \"${algo}\" \"\${F_NAME}\" > \"${hashFile}\""
        ].join("\n")

        if (!this.steps.fileExists(hashFile)) {
            throw new PackageException("Hash file ${hashFile} is not created.")
        }

        return hashFile
    } // end hash()

    /**
     * Generate hash file for a package
     *
     * @see #hash(Map)
     */
    String hash(String filename) {
        return this.hash(filename: filename)
    }
}
