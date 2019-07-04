/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */

import java.util.logging.Logger
import org.hamcrest.collection.IsMapContaining
import org.junit.*
import org.zowe.jenkins_shared_library.artifact.JFrogArtifactory
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils
import static groovy.test.GroovyAssert.*
import static org.hamcrest.CoreMatchers.*;

/**
 * Test {@link org.zowe.jenkins_shared_library.artifact.JFrogArtifactory}
 *
 * <p>Test {@code interpretArtifactDefinition} method which doesn't require a Jenkins pipeline.</p>
 */
class JFrogArtifactoryInterpretArtifactDefinitionTest {
    /**
     * Logger object
     */
    static transient Logger logger

    /**
     * Init dependencies
     */
    @BeforeClass
    public static void initDependencies() {
        // init logger
        logger = Utils.getLogger(Class.getSimpleName())
    }

    @Test
    void testInterpretArtifactDefinition() {
        def jfrog = new JFrogArtifactory()
        def packageName = 'org.zowe.test-project'
        def tests = [[
            "definition": [
                'version'     : '^1.2.3',
            ],
            "expectedPattern" : 'libs-release-local/org/zowe/test-project/1.*/*'
        ], [
            "definition": [
                'version'     : '^1.2.3-STAGING',
            ],
            "expectedPattern" : 'libs-snapshot-local/org/zowe/test-project/1.*-STAGING/*'
        ], [
            "definition": [
                'version'     : '~1.2.3',
            ],
            "expectedPattern" : 'libs-release-local/org/zowe/test-project/1.2.*/*'
        ], [
            "definition": [
                'version'     : '~1.2.3-RC',
            ],
            "expectedPattern" : 'libs-snapshot-local/org/zowe/test-project/1.2.*-RC/*'
        ], [
            "definition": [
                'version'     : '~1.2.3',
                'repository'  : 'libs-snapshot-local',
            ],
            "expectedPattern" : 'libs-snapshot-local/org/zowe/test-project/1.2.*/*'
        ], [
            "definition": [
                'version'     : '~1.2.3',
                'artifact'    : 'test-project-special-name-*.pax'
            ],
            "expectedPattern" : 'libs-release-local/org/zowe/test-project/1.2.*/test-project-special-name-*.pax'
        ], [
            "definition": [
                'artifact'    : 'libs-snapshot-local/org/zowe/zlux/zlux-core/1.3.0-RC/zlux-core-1.3.0-20190607.143930.pax'
            ],
            "expectedPattern" : 'libs-snapshot-local/org/zowe/zlux/zlux-core/1.3.0-RC/zlux-core-1.3.0-20190607.143930.pax'
        ], [
            "definition": [
                'version'     : '1.2.3',
            ],
            "expectedPattern" : 'libs-release-local/org/zowe/test-project/1.2.3/*'
        ], [
            "definition": [
                'version'     : '1.2.3',
                'artifact'    : 'test-project-special-name-*.pax'
            ],
            "expectedPattern" : 'libs-release-local/org/zowe/test-project/1.2.3/test-project-special-name-*.pax'
        ]]
        tests.each {
            Map res = jfrog.interpretArtifactDefinition(packageName, it['definition'])
            logger.fine("[testInterpretArtifactDefinition] (${it['definition']}) = (${res})")
            assertThat('download spec', res, IsMapContaining.hasKey('pattern'));
            assertThat(res['pattern'], equalTo(it['expectedPattern']))
        }

        // test failure
        def err = shouldFail InvalidArgumentException, {
            // not a semver
            jfrog.interpretArtifactDefinition(packageName, ['version': '1.2.3.4'])
        }
        logger.fine("[testInterpretArtifactDefinition] (['version':'1.2.3.4']}) = (${err.getMessage()})")
        assertThat('interpretArtifactDefinition error', err.getMessage(), containsString('not a valid semantic version.'))
    }

    @Test
    void testInterpretArtifactDefinitions() {
        def jfrog = new JFrogArtifactory()
        def definitions = [
            "org.zowe.test-project-a": [
                'version'     : '^1.2.3',
            ],
            "org.zowe.test-project-a1": [
                'version'     : '^1.2.3-STAGING',
            ],
            "org.zowe.test-project-b": [
                'version'     : '~1.2.3',
            ],
            "org.zowe.test-project-b1": [
                'version'     : '~1.2.3-RC',
            ],
            "org.zowe.test-project-c": [
                'version'     : '~1.2.3',
                'artifact'    : 'test-project-special-name-*.pax'
            ],
            "org.zowe.test-project-d": [
                'artifact'    : 'libs-snapshot-local/org/zowe/zlux/zlux-core/1.3.0-RC/zlux-core-1.3.0-20190607.143930.pax'
            ],
            "org.zowe.test-project-e": [
                'version'     : '1.2.3',
            ],
            "org.zowe.test-project-f": [
                'version'     : '1.2.3',
                'artifact'    : 'test-project-special-name-*.pax'
            ],
        ]
        Map res = jfrog.interpretArtifactDefinitions(definitions)
        logger.fine("[testInterpretArtifactDefinitions] res = ${res}")
        assertThat('download spec', res, IsMapContaining.hasKey('files'));
        assertThat(res['files'].size(), equalTo(8))
    }
}
