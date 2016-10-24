/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil


class WorkerDaemonTaskIntegrationTest extends AbstractIntegrationSpec {

    def "can specify custom action from buildSrc to perform in a daemon"() {
        def outputFileDir = file("build/worker")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
        def list = [ 1, 2, 3 ]

        file("buildSrc/src/main/java/org/gradle/test/MyAction.java") << """
            package org.gradle.test;

            import org.gradle.api.Action;
            import java.io.Serializable;
            import java.io.File;

            public class MyAction implements Action<String>, Serializable {
                public void execute(String name) {
                    File outputDir = new File("${outputFileDirPath}");
                    outputDir.mkdirs();
                    try {
                        File outputFile = new File(outputDir, name);
                        outputFile.createNewFile();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
        buildFile << """
            import org.gradle.test.MyAction;

            class MyWorkerDaemonTask extends WorkerDaemonTask {
                def list

                @TaskAction
                void taskAction() {
                    executeAllInDaemon(new MyAction(), list.collect { it as String })
                }
            }

            task runActions(type: MyWorkerDaemonTask) {
                list = ${list}
            }
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
            // Need something here to validate that all actions ran in the same daemon
        }
    }

    @NotYetImplemented
    def "can specify custom transform from buildSrc to perform in a daemon"() {
        def outputFileDir = file("build/worker")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
        def list = [ 1, 2, 3 ]

        file("buildSrc/src/main/java/org/gradle/test/MyTransformer.java") << """
            package org.gradle.test;

            import org.gradle.api.Transformer;
            import java.io.Serializable;
            import java.io.File;

            public class MyTransformer implements Transformer<File, String>, Serializable {
                public File transform(String name) {
                    File outputDir = new File("${outputFileDirPath}");
                    outputDir.mkdirs();
                    try {
                        File outputFile = new File(outputDir, name);
                        outputFile.createNewFile();
                        return outputFile;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
        buildFile << """
            import org.gradle.test.MyTransformer;

            class MyWorkerDaemonTask extends WorkerDaemonTask {
                def list

                @TaskAction
                void taskAction() {
                    List<File> files = executeAllInDaemon(new MyTransformer(), list.collect { it as String })
                    list.each { subject ->
                        assert files.contains(file("${outputFileDirPath}/\${subject}"))
                    }
                }
            }

            task runActions(type: MyWorkerDaemonTask) {
                list = ${list}
            }
        """

        when:
        succeeds("runActions")

        then:
        list.each {
            outputFileDir.file(it).assertExists()
            // Need something here to validate that all actions ran in the same daemon
        }
    }

    @NotYetImplemented
    def "can specify custom action from build script to perform in a worker task"() {
        def outputFileDir = file("build/worker")
        def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
        def specs = [ 1, 2, 3 ]

        buildFile << """
            class MyAction implements Action<String>, Serializable {
                public void execute(String name) {
                    File outputDir = new File("${outputFileDirPath}")
                    outputDir.mkdirs()
                    try {
                        File outputFile = new File(outputDir, name)
                        outputFile.createNewFile()
                    } catch (Exception e) {
                        throw new RuntimeException(e)
                    }
                }
            }

            class MyWorkerDaemonTask extends WorkerDaemonTask {
                def specs
                def action = { String name ->
                    def outputDir = project.file("${outputFileDirPath}")
                    outputDir.mkdirs()
                    new File(outputDir, name).createNewFile()
                }

                @TaskAction
                void taskAction() {
                    executeAllInDaemon(action, specs.collect { it as String })
                }
            }

            task runActions(type: MyWorkerDaemonTask) {
                specs = ${specs}
            }
        """

        when:
        succeeds("runActions")

        then:
        specs.each {
            outputFileDir.file(it).assertExists()
            // Need something here to validate that all actions ran in the same daemon
        }
    }
}
