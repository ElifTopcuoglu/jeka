package dev.jeka.core.api.java;

import dev.jeka.core.api.depmanagement.JkModuleFileProxy;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.utils.JkUtilsReflect;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Not part of public api
 */
public interface JkInternalClasspathScanner {

    static JkInternalClasspathScanner of() {
        return Cache.get();
    }

    List<String> findClassesHavingMainMethod(ClassLoader extraClassLoader);

    List<String> findClassesMatchingAnnotations(ClassLoader classloader, Predicate<List<String>> annotationPredicate);

    List<String> findClassedExtending(ClassLoader classLoader, Class<?> baseClass,
                                      Predicate<String> classpathElementFilter, boolean ignoreVisibility,
                                      boolean ignoreParentClassloaders);

    Set<Class<?>> loadClassesHavingSimpleNameMatching(Predicate<String> predicate);

    <T> Class<T> loadFirstFoundClassHavingNameOrSimpleName(String name, Class<T> superClass);

    default Set<Class<?>> loadClassesHavingSimpleName(String simpleName) {
        return loadClassesHavingSimpleNameMatching( name -> name.equals(simpleName));
    }

    JkPathSequence getClasspath(ClassLoader classLoader);

    static class Cache {

        private static JkInternalClasspathScanner CACHED_INSTANCE;

        private static JkInternalClasspathScanner get() {
            if (CACHED_INSTANCE != null) {
                return CACHED_INSTANCE;
            }
            String IMPL_CLASS = "dev.jeka.core.api.java.embedded.classgraph.ClassGraphClasspathScanner";
            Class<JkInternalClasspathScanner> clazz = JkClassLoader.ofCurrent().loadIfExist(IMPL_CLASS);
            if (clazz != null) {
                return JkUtilsReflect.invokeStaticMethod(clazz, "of");
            }
            JkModuleFileProxy classgraphJar = JkModuleFileProxy.ofStandardRepos("io.github.classgraph:classgraph:4.8.41");
            JkInternalClassloader internalClassloader = JkInternalClassloader.ofMainEmbeddedLibs(classgraphJar.get());
            CACHED_INSTANCE = internalClassloader
                    .createCrossClassloaderProxy(JkInternalClasspathScanner.class, IMPL_CLASS, "of");
            return CACHED_INSTANCE;
        }

    }

}
