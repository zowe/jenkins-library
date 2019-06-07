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
import org.zowe.jenkins_shared_library.exceptions.InvalidArgumentException
import org.zowe.jenkins_shared_library.Utils
import static groovy.test.GroovyAssert.*
import static org.hamcrest.CoreMatchers.*;

/**
 * Test {@link org.zowe.jenkins_shared_library.Utils}
 */
class UtilsTest {
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
    void testSanitizeBranchName() {
        def test1 = Utils.sanitizeBranchName('Some_Branch_Name_In_CamelCase')
        logger.fine("[testSanitizeBranchName] test1 = \"${test1}\"")
        assertThat(test1, equalTo('some-branch-name-in-camelcase'))

        def test2 = Utils.sanitizeBranchName('special+chars=<ok>')
        logger.fine("[testSanitizeBranchName] test2 = \"${test2}\"")
        assertThat(test2, equalTo('special-chars-ok-'))
    }

    @Test
    void testParseSemanticVersion() {
        // test success
        def test1 = Utils.parseSemanticVersion('1.2.3-beta.1+56789')
        logger.fine("[testParseSemanticVersion] test1 = \"${test1}\"")
        assertThat('version trunks', test1, IsMapContaining.hasKey('major'));
        assertThat('version trunks', test1, IsMapContaining.hasKey('minor'));
        assertThat('version trunks', test1, IsMapContaining.hasKey('patch'));
        assertThat('version trunks', test1, IsMapContaining.hasKey('prerelease'));
        assertThat('version trunks', test1, IsMapContaining.hasKey('metadata'));
        assertThat(test1['major'], equalTo(1))
        assertThat(test1['minor'], equalTo(2))
        assertThat(test1['patch'], equalTo(3))
        assertThat(test1['prerelease'], equalTo('beta.1'))
        assertThat(test1['metadata'], equalTo('56789'))

        // test failure
        def err = shouldFail InvalidArgumentException, {
            // not a semver
            Utils.parseSemanticVersion('1.2.3.4')
        }
        assertThat('parseSemanticVersion error', err.getMessage(), containsString('not a valid semantic version.'))
    }

    @Test
    void testGetTimestamp() {
        def ts1 = Utils.getTimestamp()
        logger.fine("[testGetTimestamp] ts1 = \"${ts1}\"")
        assertTrue(ts1.matches('^20[0-9]{12}$'));

        def ts2 = Utils.getTimestamp('yyyy-MM-dd HH:mm:ss')
        logger.fine("[testGetTimestamp] ts2 = \"${ts2}\"")
        assertTrue(ts2.matches('^20[0-9]{2}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$'))
    }

    @Test
    void testGetBuildIdentifier() {
        def env = [
            BRANCH_NAME  : 'test_branch',
            BUILD_NUMBER : 100,
        ]

        // test default values
        def test0 = Utils.getBuildIdentifier(env)
        logger.fine("[testGetBuildIdentifier] test0 = \"${test0}\"")
        assertTrue(test0.matches('^20[0-9]{6}\\.[0-9]{6}-test-branch-100$'))

        // test includeTimestamp
        def test10 = Utils.getBuildIdentifier(env, [
                includeTimestamp: 'yyyyMMddHHmmss',
            ])
        logger.fine("[testGetBuildIdentifier] test10 = \"${test10}\"")
        assertTrue(test10.matches('^20[0-9]{12}-test-branch-100$'))

        def test11 = Utils.getBuildIdentifier(env, [
                includeTimestamp: false,
            ])
        logger.fine("[testGetBuildIdentifier] test11 = \"${test11}\"")
        assertThat(test11, equalTo('test-branch-100'))

        def test12 = Utils.getBuildIdentifier(env, [
                includeTimestamp: true,
            ])
        logger.fine("[testGetBuildIdentifier] test12 = \"${test12}\"")
        assertTrue(test12.matches('^20[0-9]{6}\\.[0-9]{6}-test-branch-100$'))

        // test excludeBranches
        def test20 = Utils.getBuildIdentifier(env, [
                excludeBranches: '__ALL__',
            ])
        logger.fine("[testGetBuildIdentifier] test20 = \"${test20}\"")
        assertTrue(test20.matches('^20[0-9]{6}\\.[0-9]{6}-100$'))

        def test21 = Utils.getBuildIdentifier(env, [
                excludeBranches: 'test_branch',
            ])
        logger.fine("[testGetBuildIdentifier] test21 = \"${test21}\"")
        assertTrue(test21.matches('^20[0-9]{6}\\.[0-9]{6}-100$'))

        def test22 = Utils.getBuildIdentifier(env, [
                excludeBranches: 'master',
            ])
        logger.fine("[testGetBuildIdentifier] test22 = \"${test22}\"")
        assertTrue(test22.matches('^20[0-9]{6}\\.[0-9]{6}-test-branch-100$'))

        def test23 = Utils.getBuildIdentifier(env, [
                excludeBranches: ['master', 'test_branch'],
            ])
        logger.fine("[testGetBuildIdentifier] test23 = \"${test23}\"")
        assertTrue(test23.matches('^20[0-9]{6}\\.[0-9]{6}-100$'))

        // test excludeBranches
        def test30 = Utils.getBuildIdentifier(env, [
                includeBuildNumber: false,
            ])
        logger.fine("[testGetBuildIdentifier] test30 = \"${test30}\"")
        assertTrue(test30.matches('^20[0-9]{6}\\.[0-9]{6}-test-branch$'))
    }

    @Test
    void testGetReleaseIdentifier() {
        def env = [
            BRANCH_NAME  : 'test_branch',
        ]

        // test default values
        def test00 = Utils.getReleaseIdentifier(env)
        logger.fine("[testGetReleaseIdentifier] test00 = \"${test00}\"")
        assertThat(test00, equalTo('test-branch'))

        def test01 = Utils.getReleaseIdentifier([BRANCH_NAME: 'master'])
        logger.fine("[testGetReleaseIdentifier] test01 = \"${test01}\"")
        assertThat(test01, equalTo('SNAPSHOT'))

        // test mappings
        def test10 = Utils.getReleaseIdentifier(env, ['test-branch': 'edge', 'master': 'SNAPSHOT'])
        logger.fine("[testGetReleaseIdentifier] test10 = \"${test10}\"")
        assertThat(test10, equalTo('edge'))
    }

    @Test
    void testParseFileExtension() {
        // test success
        def test1 = Utils.parseFileExtension('/path/to/my/special-file.zip')
        logger.fine("[testParseFileExtension] test1 = \"${test1}\"")
        assertThat('name ext', test1, IsMapContaining.hasKey('name'));
        assertThat('name ext', test1, IsMapContaining.hasKey('ext'));
        assertThat(test1['name'], equalTo('special-file'))
        assertThat(test1['ext'], equalTo('.zip'))

        // test success
        def test2 = Utils.parseFileExtension('/path/to/my/special-file-without-ext')
        logger.fine("[testParseFileExtension] test2 = \"${test2}\"")
        assertThat('name ext', test2, IsMapContaining.hasKey('name'));
        assertThat('name ext', test2, IsMapContaining.hasKey('ext'));
        assertThat(test2['name'], equalTo('special-file-without-ext'))
        assertThat(test2['ext'], equalTo(''))

        // test success
        def test3 = Utils.parseFileExtension('/path/to/my/special-file.tar.gz')
        logger.fine("[testParseFileExtension] test3 = \"${test3}\"")
        assertThat('name ext', test3, IsMapContaining.hasKey('name'));
        assertThat('name ext', test3, IsMapContaining.hasKey('ext'));
        assertThat(test3['name'], equalTo('special-file'))
        assertThat(test3['ext'], equalTo('.tar.gz'))

        // test success
        def test4 = Utils.parseFileExtension('/path/to/my/special-file.pax.Z')
        logger.fine("[testParseFileExtension] test4 = \"${test4}\"")
        assertThat('name ext', test4, IsMapContaining.hasKey('name'));
        assertThat('name ext', test4, IsMapContaining.hasKey('ext'));
        assertThat(test4['name'], equalTo('special-file'))
        assertThat(test4['ext'], equalTo('.pax.Z'))
    }

    @Test
    void testGetUriQueryString() {
        def test1 = Utils.getUriQueryString(['a': 1, 'b': 2])
        logger.fine("[testGetUriQueryString] test1 = \"${test1}\"")
        assertThat(test1, equalTo('a=1&b=2'))

        def test2 = Utils.getUriQueryString(['a': 1, 'b': 2, 'c': '"="'])
        logger.fine("[testGetUriQueryString] test2 = \"${test2}\"")
        assertThat(test2, equalTo('a=1&b=2&c=%22%3D%22'))
    }

    @Test
    void testEscapeXml() {
        // test default values
        def test1 = Utils.escapeXml("\"hello\"=<ok>")
        logger.fine("[testEscapeXml] test1 = \"${test1}\"")
        assertThat(test1, equalTo('&quot;hello&quot;=&lt;ok&gt;'))
    }
}
