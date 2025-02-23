/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.jvm.toolchain.internal

import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

@CleanupTestDirectory
class MavenToolchainsInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());

    def userHome = temporaryFolder.getTestDirectory().getCanonicalPath()
    def mavenHome = temporaryFolder.createDir(".m2")

    def "supplies no installations for non-existing directory"(boolean useProperty) {
        assert mavenHome.delete()

        given:
        def supplier = useProperty ? createSupplier(mavenHome.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies no installations for empty directory"(boolean useProperty) {
        given:
        def supplier = useProperty ? createSupplier(mavenHome.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies no installations empty toolchains file"(boolean useProperty) {
        given:
        def toolchains = mavenHome.createFile(new File("toolchains.xml"))
        def supplier = useProperty ? createSupplier(toolchains.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies no installations non-xml toolchains file"(boolean useProperty) {
        given:
        def toolchains = mavenHome.createFile(new File("toolchains.xml"))
        toolchains.write("this is not xml")
        def supplier = useProperty ? createSupplier(toolchains.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies no installations toolchains file with non-matching contents"(boolean useProperty) {
        given:
        def toolchains = mavenHome.createFile(new File("toolchains.xml"))
        toolchains.write('''<toolchains>
                                <toolchain>
                                <type>example</type>
                                <configuration>
                                <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
                                </configuration>
                                </toolchain>
                                </toolchains>''')
        def supplier = useProperty ? createSupplier(toolchains.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies single installations for single candidate"(boolean useProperty, String toolchain) {
        given:
        def toolchains = mavenHome.createFile(new File("toolchains.xml"))
        toolchains.write(toolchain)
        def supplier = useProperty ? createSupplier(toolchains.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File("/usr/lib/jvm/adoptopenjdk-16.jdk").absolutePath])
        directories*.source == ["Maven Toolchains"]

        where:
        [useProperty, toolchain] << [[true, false], validToolchains()].combinations()
    }

    def "supplies multiple installations for multiple paths"(boolean useProperty) {
        given:
        def toolchain = '''<toolchains>
                                <toolchain>
                                <type>jdk</type>
                                <configuration>
                                <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
                                </configuration>
                                </toolchain>
                                <toolchain>
                                <type>jdk</type>
                                <configuration>
                                <jdkHome>/usr/lib/jvm/temurin-17.jdk</jdkHome>
                                </configuration>
                                </toolchain>
                                </toolchains>'''
        def toolchains = mavenHome.createFile(new File("toolchains.xml"))
        toolchains.write(toolchain)
        def supplier = useProperty ? createSupplier(toolchains.getCanonicalPath(), null) : createSupplier(null, userHome)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File("/usr/lib/jvm/adoptopenjdk-16.jdk").absolutePath,
            new File("/usr/lib/jvm/temurin-17.jdk").absolutePath
        ])
        directories*.source == ["Maven Toolchains", "Maven Toolchains"]

        where:
        useProperty << [true, false]
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    InstallationSupplier createSupplier(String propertyValue, String userhome) {
        SystemProperties.instance.withSystemProperty("user.home", userhome) {
            new MavenToolchainsInstallationSupplier(createProviderFactory(propertyValue))
        }
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable(null)
        providerFactory.gradleProperty("org.gradle.java.installations.maven-toolchains-file") >> Providers.ofNullable(propertyValue)
        providerFactory
    }

    private static Collection<String> validToolchains() {
        ['''<toolchains>
            <toolchain>
            <type>jdk</type>
            <configuration>
            <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
            </configuration>
            </toolchain>
            </toolchains>''',
         '''<?xml version="1.0" encoding="UTF-8"?>
            <toolchains>
            <toolchain>
            <type>jdk</type>
            <configuration>
            <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
            </configuration>
            </toolchain>
            </toolchains>''',
         '''<?xml version="1.0" encoding="UTF-8"?>
            <toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
            <toolchain>
            <type>jdk</type>
            <configuration>
            <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
            </configuration>
            </toolchain>
            </toolchains>''',
         '''<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
            <toolchain>
            <type>jdk</type>
            <configuration>
            <jdkHome>/usr/lib/jvm/adoptopenjdk-16.jdk</jdkHome>
            </configuration>
            </toolchain>
            </toolchains>''',
        ]
    }
}
