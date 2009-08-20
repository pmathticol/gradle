/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaPlugin implements Plugin {
    public static final String PROCESS_RESOURCES_TASK_NAME = "processResources";
    public static final String COMPILE_TASK_NAME = "compile";
    public static final String PROCESS_TEST_RESOURCES_TASK_NAME = "processTestResources";
    public static final String COMPILE_TESTS_TASK_NAME = "compileTests";
    public static final String TEST_TASK_NAME = "test";
    public static final String JAR_TASK_NAME = "jar";
    public static final String LIBS_TASK_NAME = "libs";
    public static final String DISTS_TASK_NAME = "dists";
    public static final String JAVADOC_TASK_NAME = "javadoc";
    public static final String BUILD_TASK_NAME = "build";
    public static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";
    public static final String BUILD_NEEDED_TASK_NAME = "buildNeeded";

    public static final String COMPILE_CONFIGURATION_NAME = "compile";
    public static final String RUNTIME_CONFIGURATION_NAME = "runtime";
    public static final String TEST_RUNTIME_CONFIGURATION_NAME = "testRuntime";
    public static final String TEST_COMPILE_CONFIGURATION_NAME = "testCompile";
    public static final String DISTS_CONFIGURATION_NAME = "dists";

    public static final String MAIN_SOURCE_SET_NAME = "main";
    public static final String TEST_SOURCE_SET_NAME = "test"; 

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(BasePlugin.class, project);
        projectPluginsHandler.usePlugin(ReportingBasePlugin.class, project);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project);
        Convention convention = project.getConvention();
        convention.getPlugins().put("java", javaConvention);

        configureConfigurations(project);
        configureSourceSets(javaConvention);

        configureJavaDoc(project);

        configureProcessResources(project);
        configureCompile(project);

        configureTest(project);

        configureProcessTestResources(project);
        configureTestCompile(project);

        configureArchives(project);

        configureBuild(project);
        configureBuildNeeded(project);
        configureBuildDependents(project);
    }

    private void configureSourceSets(final JavaPluginConvention pluginConvention) {
        pluginConvention.getSource().allObjects(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                ConventionMapping conventionMapping = ((IConventionAware) sourceSet).getConventionMapping();
                conventionMapping.map("classesDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        String classesDirName = String.format("classes/%s", sourceSet.getName());
                        return new File(pluginConvention.getProject().getBuildDir(), classesDirName);
                    }
                });
            }
        });
        pluginConvention.getSource().add(MAIN_SOURCE_SET_NAME);
        pluginConvention.getSource().add(TEST_SOURCE_SET_NAME);
    }

    private void configureTestCompile(Project project) {
        configureCompileTests(project.getTasks().add(COMPILE_TESTS_TASK_NAME, Compile.class),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations()).setDescription("Compiles the Java test source code.");
    }

    private void configureCompile(final Project project) {
        project.getTasks().withType(Compile.class).allTasks(new Action<Compile>() {
            public void execute(Compile compile) {
                compile.setClasspath(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                compile.getConventionMapping().map(DefaultConventionsToPropertiesMapping.COMPILE);
                addDependsOnProjectBuildDependencies(compile, COMPILE_CONFIGURATION_NAME);
            }
        });

        project.getTasks().add(COMPILE_TASK_NAME, Compile.class).setDescription("Compiles the Java source code.");
    }

    private void configureProcessResources(Project project) {
        Copy processResources = project.getTasks().add(PROCESS_RESOURCES_TASK_NAME, Copy.class);
        processResources.getConventionMapping().map(DefaultConventionsToPropertiesMapping.RESOURCES);
        processResources.setDescription(
                "Process and copy the resources into the binary directory of the compiled sources.");
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().withType(Javadoc.class).allTasks(new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map(DefaultConventionsToPropertiesMapping.JAVADOC);
                javadoc.setConfiguration(project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME));
                addDependsOnTaskInOtherProjects(javadoc, true, JAVADOC_TASK_NAME, COMPILE_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(JAVADOC_TASK_NAME, Javadoc.class).setDescription("Generates the javadoc for the source code.");
    }

    private void configureProcessTestResources(Project project) {
        ConventionTask processTestResources = project.getTasks().add(PROCESS_TEST_RESOURCES_TASK_NAME, Copy.class);
        processTestResources.getConventionMapping().map(DefaultConventionsToPropertiesMapping.TEST_RESOURCES);
        processTestResources.setDescription(
                "Process and copy the test resources into the binary directory of the compiled test sources.");
    }

    private void configureArchives(final Project project) {
        project.getTasks().withType(AbstractArchiveTask.class).allTasks(new Action<AbstractArchiveTask>() {
            public void execute(AbstractArchiveTask task) {
                if (task instanceof Jar) {
                    task.getConventionMapping().map(DefaultConventionsToPropertiesMapping.JAR);
                    task.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                    task.dependsOn(COMPILE_TASK_NAME);
                }
                else if (task instanceof Tar) {
                    task.getConventionMapping().map(DefaultConventionsToPropertiesMapping.TAR);
                    task.dependsOn(LIBS_TASK_NAME);
                }
                else if (task instanceof Zip) {
                    task.getConventionMapping().map(DefaultConventionsToPropertiesMapping.ZIP);
                    task.dependsOn(LIBS_TASK_NAME);
                }
            }
        });

        final Spec<Task> isLib = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element instanceof Jar;
            }
        };
        Task libsTask = project.getTasks().add(LIBS_TASK_NAME);
        libsTask.setDescription("Builds all Jar and War archives");
        libsTask.dependsOn(new TaskDependency(){
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().findAll(isLib);
            }
        });

        final Spec<Task> isDist = new Spec<Task>() {
            public boolean isSatisfiedBy(Task element) {
                return element instanceof Zip && !isLib.isSatisfiedBy(element);
            }
        };
        Task distsTask = project.getTasks().add(DISTS_TASK_NAME);
        distsTask.setDescription("Builds all Jar, War, Zip, and Tar archives");
        distsTask.dependsOn(LIBS_TASK_NAME);
        distsTask.dependsOn(new TaskDependency(){
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().findAll(isDist);
            }
        });

        Jar jar = project.getTasks().add(JAR_TASK_NAME, Jar.class);
        jar.setDescription("Generates a jar archive with all the compiled classes.");
        jar.conventionMapping("resourceCollections", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                File classesDir = convention.getPlugin(JavaPluginConvention.class).getSource().getByName(
                        JavaPlugin.MAIN_SOURCE_SET_NAME).getClassesDir();
                return WrapUtil.toList((Object) new FileSet(classesDir));
            }
        });
        project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).addArtifact(new ArchivePublishArtifact(jar));
    }

    private void configureBuild(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Builds and tests this project");
        buildTask.dependsOn(DISTS_TASK_NAME);
        buildTask.dependsOn(TEST_TASK_NAME);
        addDependsOnProjectBuildDependencies(buildTask, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureBuildNeeded(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_NEEDED_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Builds and tests this project and all projects it depends on");
        buildTask.dependsOn(BUILD_TASK_NAME);
        addDependsOnTaskInOtherProjects(buildTask, true, BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureBuildDependents(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_DEPENDENTS_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Builds and tests this project and all projects that depend on it");
        buildTask.dependsOn(BUILD_TASK_NAME);
        addDependsOnTaskInOtherProjects(buildTask, false, BUILD_TASK_NAME, TEST_RUNTIME_CONFIGURATION_NAME);
    }

    private void configureTest(final Project project) {
        project.getTasks().withType(Test.class).allTasks(new Action<Test>() {
            public void execute(Test test) {
                test.dependsOn(COMPILE_TESTS_TASK_NAME);
                test.dependsOn(PROCESS_TEST_RESOURCES_TASK_NAME);
                test.dependsOn(COMPILE_TASK_NAME);
                test.dependsOn(PROCESS_RESOURCES_TASK_NAME);
                test.getConventionMapping().map(DefaultConventionsToPropertiesMapping.TEST);
                test.setConfiguration(project.getConfigurations().getByName(TEST_RUNTIME_CONFIGURATION_NAME));
                addDependsOnProjectBuildDependencies(test, TEST_RUNTIME_CONFIGURATION_NAME);
            }
        });
        project.getTasks().add(TEST_TASK_NAME, Test.class).setDescription("Runs the tests.");
    }

    void configureConfigurations(final Project project) {
        project.setProperty("status", "integration");
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration compileConfiguration = configurations.add(COMPILE_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("Classpath for compiling the sources.");
        Configuration runtimeConfiguration = configurations.add(RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setDescription("Classpath for running the compiled sources.");

        Configuration compileTestsConfiguration = configurations.add(TEST_COMPILE_CONFIGURATION_NAME).setVisible(false).extendsFrom(compileConfiguration).
                setTransitive(false).setDescription("Classpath for compiling the test sources.");
        project.getDependencies().add(TEST_COMPILE_CONFIGURATION_NAME, new AbstractFileCollection() {
            public String getDisplayName() {
                return "classes dir";
            }
            public Set<File> getFiles() {
                File classesDir = project.getConvention().getPlugin(JavaPluginConvention.class).getSource().getByName(JavaPlugin.MAIN_SOURCE_SET_NAME).getClassesDir();
                return Collections.singleton(classesDir);
            }
        });
        
        configurations.add(TEST_RUNTIME_CONFIGURATION_NAME).setVisible(false).extendsFrom(runtimeConfiguration, compileTestsConfiguration).
                setDescription("Classpath for running the test sources.");
        
        Configuration archivesConfiguration = configurations.add(Dependency.ARCHIVES_CONFIGURATION).
                setDescription("Configuration for the default artifacts.");
        configurations.add(Dependency.DEFAULT_CONFIGURATION).extendsFrom(runtimeConfiguration, archivesConfiguration).
                setDescription("Configuration the default artifacts and its dependencies.");
        configurations.add(DISTS_CONFIGURATION_NAME);
    }

    protected Compile configureCompileTests(Compile compileTests, Map propertyMapping, ConfigurationContainer configurations) {
        compileTests.setDependsOn(WrapUtil.toSet(COMPILE_TASK_NAME));
        configureCompileInternal(compileTests, propertyMapping);
        compileTests.setClasspath(configurations.getByName(TEST_COMPILE_CONFIGURATION_NAME));
        addDependsOnProjectBuildDependencies(compileTests, TEST_COMPILE_CONFIGURATION_NAME);
        return compileTests;
    }

    protected Compile configureCompileInternal(Compile compile, Map propertyMapping) {
        compile.getConventionMapping().map(propertyMapping);
        return compile;
    }

    private void addDependsOnProjectBuildDependencies(final Task task, String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getBuildDependencies());
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects are determined from
     * project lib dependencies using the specified configuration name. These may be projects this project depends on
     * or projects that depend on this project based on the useDependOn argument.
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise use projects
     *                      that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn, String otherProjectTaskName, String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(useDependedOn, otherProjectTaskName));
    }

    protected JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}
