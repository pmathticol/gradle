/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.file.DefaultSourceDirectorySet
import org.gradle.api.tasks.bundling.GradleManifest
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.internal.tasks.DefaultSourceSetContainer

/**
 * @author Hans Dockter
 */
// todo Think about moving the mkdir method to the project.
// todo Refactor to Java
class JavaPluginConvention {
    Project project

    String srcRootName
    String classesDirName
    String dependencyCacheDirName
    String docsDirName
    String javadocDirName
    String testResultsDirName
    String testReportDirName
    List srcDirNames = []
    List resourceDirNames = []
    List testSrcDirNames = []
    List testResourceDirNames = []
    List floatingSrcDirs = []
    List floatingTestSrcDirs = []
    List floatingResourceDirs = []
    List floatingTestResourceDirs = []
    final SourceSetContainer source
    
    /**
     * All java source to be compiled for this project.
     */
    SourceDirectorySet src
    /**
     * All java test source to be compiled for this project.
     */
    SourceDirectorySet testSrc
    /**
     * All java source for this project. This includes, for example, source which is directly compiled, and source which
     * is indirectly compiled through joint compilation.
     */
    UnionFileTree allJavaSrc
    /**
     * All java test source for this project. This includes, for example, source which is directly compiled, and source which
     * is indirectly compiled through joint compilation.
     */
    UnionFileTree allJavaTestSrc

    private JavaVersion srcCompat
    private JavaVersion targetCompat
    GradleManifest manifest
    List metaInf

    JavaPluginConvention(Project project) {
        this.project = project
        source = new DefaultSourceSetContainer(project.fileResolver)
        manifest = new GradleManifest()
        metaInf = []
        srcRootName = 'src'
        dependencyCacheDirName = 'dependency-cache'
        docsDirName = 'docs'
        javadocDirName = 'javadoc'
        testResultsDirName = 'test-results'
        testReportDirName = 'tests'
        srcDirNames << 'main/java'
        resourceDirNames << 'main/resources'
        testSrcDirNames << 'test/java'
        testResourceDirNames << 'test/resources'
        src = new DefaultSourceDirectorySet('main java source', project.fileResolver)
        src.srcDirs { -> srcDirs }
        allJavaSrc = new UnionFileTree('main java source', src)
        testSrc = new DefaultSourceDirectorySet('test java source', project.fileResolver)
        testSrc.srcDirs {-> testSrcDirs }
        allJavaTestSrc = new UnionFileTree('test java source', testSrc)
    }

    def source(Closure closure) {
        source.configure(closure)
    }
    
    File mkdir(File parent = null, String name) {
        if (!name) {throw new InvalidUserDataException('You must specify the name of the directory')}
        File baseDir = parent ?: project.buildDir
        File result = new File(baseDir, name)
        result.mkdirs()
        result
    }

    File getSrcRoot() {
        project.file(srcRootName)
    }

    List getSrcDirs() {
        srcDirNames.collect {new File(srcRoot, it)} + floatingSrcDirs
    }

    List getResourceDirs() {
        resourceDirNames.collect {new File(srcRoot, it)} + floatingResourceDirs
    }

    List getTestSrcDirs() {
        testSrcDirNames.collect {new File(srcRoot, it)} + floatingTestSrcDirs
    }

    List getTestResourceDirs() {
        testResourceDirNames.collect {new File(srcRoot, it)} + floatingTestResourceDirs
    }

    File getDependencyCacheDir() {
        new File(project.buildDir, dependencyCacheDirName)
    }

    File getDocsDir() {
        new File(project.buildDir, docsDirName)
    }

    File getJavadocDir() {
        new File(docsDir, javadocDirName)
    }

    File getTestResultsDir() {
        new File(project.buildDir, testResultsDirName)
    }

    File getTestReportDir() {
        new File(reportsDir, testReportDirName)
    }

    private File getReportsDir() {
        project.convention.plugins.reportingBase.reportsDir
    }

    JavaVersion getSourceCompatibility() {
        srcCompat ?: JavaVersion.VERSION_1_5
    }

    void setSourceCompatibility(def value) {
        srcCompat = JavaVersion.toVersion(value)
    }

    JavaVersion getTargetCompatibility() {
        targetCompat ?: sourceCompatibility
    }

    void setTargetCompatibility(def value) {
        targetCompat = JavaVersion.toVersion(value)
    }
}
