package org.gradle.api.internal.tasks

import org.gradle.api.tasks.SourceSet
import org.gradle.api.internal.AutoCreateDomainObjectContainer
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.DefaultClassGenerator

class DefaultSourceSetContainer extends AutoCreateDomainObjectContainer<SourceSet> implements SourceSetContainer {
    private final FileResolver fileResolver;
    private final ClassGenerator generator = new DefaultClassGenerator();

    def DefaultSourceSetContainer(FileResolver fileResolver) {
        super(SourceSet.class);
        this.fileResolver = fileResolver;
    }

    @Override
    protected SourceSet create(String name) {
        return generator.newInstance(DefaultSourceSet.class, name, name, fileResolver);
    }

    // These are here to keep Groovy 1.6.3 happy

    def SourceSet add(String name) {
        super.add(name)
    }

    def SourceSet add(String name, Closure configureClosure) {
        super.add(name, configureClosure)
    }
}