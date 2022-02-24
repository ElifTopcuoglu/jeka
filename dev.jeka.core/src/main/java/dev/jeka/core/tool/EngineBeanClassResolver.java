package dev.jeka.core.tool;

import dev.jeka.core.api.file.JkPathFile;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.java.JkClassLoader;
import dev.jeka.core.api.java.JkInternalClasspathScanner;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsPath;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/*
 * A resolver to determine witch {@link JkBean} class to use as default or according KBean short name.
 *
 * @author Jerome Angibaud
 */
final class EngineBeanClassResolver {

    private static final String JAVA_HOME = JkUtilsPath.toUrl(Paths.get(System.getProperty("java.home"))).toString();

    private static final String CACHE_FILENAME = "kbean-classes.txt";

    private final Path baseDir;

    final Path defSourceDir;

    final Path defClassDir;

    private JkPathSequence classpath;

    private List<String> cachedDefBeanClassNames;

    private List<String> cachedGlobalBeanClassName;

    private boolean useStoredCache;

    private static final Comparator<Class> CLASS_NAME_COMPARATOR = Comparator.comparing(Class::getName);

    EngineBeanClassResolver(Path baseDir) {
        super();
        this.baseDir = baseDir;
        this.defSourceDir = baseDir.resolve(JkConstants.DEF_DIR);
        this.defClassDir = baseDir.resolve(JkConstants.DEF_BIN_DIR);
    }

    List<EngineCommand> resolve(CommandLine commandLine, String defaultBeanName) {
        JkLog.startTask("Resolve KBean classes");
        Map<String, Class<? extends JkBean>> beanClasses = new HashMap<>();
        for (String beanName : commandLine.involvedBeanNames()) {
            List<String> beanClassNames = JkUtilsIterable.concatLists(defBeanClassNames(), globalBeanClassNames())
                    .stream().distinct().collect(Collectors.toList());
            List<String> matchingClassNames = findClassesMatchingName(beanClassNames, beanName);
            Class<? extends JkBean> selected = loadUniqueClassOrFail(matchingClassNames, beanName);
            beanClasses.put(JkBean.name(selected), selected);
        }
        Class<? extends JkBean> defaultBeanClass = defaultBeanClass(defaultBeanName);
        List<EngineCommand> result = new LinkedList<>();
        if (defaultBeanClass == null && commandLine.containsDefaultBean()) {
            throw new JkException("Cannot find any KBean in jeka/def dir. Use -kb=[beanName] to precise a " +
                    "bean present in classpath or create a class extending JkBean into jeka/def dir.");
        }
        beanClasses.put(null, defaultBeanClass);
        if (defaultBeanClass != null) {
            result.add(new EngineCommand(EngineCommand.Action.BEAN_INSTANTIATION, defaultBeanClass,
                    null, null));
        }
        commandLine.getBeanActions().stream()
                .map(action -> toEngineCommand(action, beanClasses)).forEach(result::add);
        JkLog.endTask();
        return Collections.unmodifiableList(result);
    }

    void setClasspath(JkPathSequence classpath, boolean classpathChanged) {
        this.classpath = classpath;
        this.useStoredCache = !classpathChanged;
    }

    private Class<? extends JkBean> defaultBeanClass(String defaultBeanName) {
        if (defaultBeanName == null) {
            if (defBeanClassNames().isEmpty()) {
                return null;
            }
            return defBeanClasses().get(0);
        }
        List<String> matchingclassNames = findClassesMatchingName(defBeanClassNames(), defaultBeanName);
        if (matchingclassNames.isEmpty()) {
            matchingclassNames = findClassesMatchingName(globalBeanClassNames(), defaultBeanName);
        }
        return loadUniqueClassOrFail(matchingclassNames, defaultBeanName);
    }

    private Class<? extends JkBean> loadUniqueClassOrFail(List<String> matchingBeanClasses, String beanName) {
        if (matchingBeanClasses.isEmpty()) {
            throw beanClassNotFound(beanName);
        } else if (matchingBeanClasses.size() > 1) {
            throw new JkException("Several classes matches default bean name '" + beanName + "' : "
                    + matchingBeanClasses + ". Please precise the fully qualified class name of the default bean " +
                    "instead of its short name.");
        } else {
            return JkClassLoader.ofCurrent().load(matchingBeanClasses.get(0));
        }
    }

    private JkException beanClassNotFound(String name) {
        return new JkException("Can not find a KBean named '" + name
                + "'.\nUse the name can be the fully qualified class name of the KBean, its uncapitalized "
                + "simple class name or its uncapitalized simple class name without the 'JkBean' suffix.\n"
                + "Execute jeka -help to display available beans.\n"
                + "Available KBeans :\n  " + String.join("\n  ",globalBeanClassNames())
                + "\nCurrent classloader :\n"
                + JkClassLoader.ofCurrent()
                + "\n");

    }

    List<String> globalBeanClassNames() {
        if (cachedGlobalBeanClassName == null) {
            if (useStoredCache) {
                List<String> storedClassNames = readKbeanClasses();
                if (!storedClassNames.isEmpty()) {
                    cachedGlobalBeanClassName = storedClassNames;
                    return cachedGlobalBeanClassName;
                }
            }
            long t0 = System.currentTimeMillis();
            ClassLoader classLoader = JkClassLoader.ofCurrent().get();
            boolean ignoreParent = false;
            if (classpath != null) {

                // If classpath is set, then sources has been compiled in work dir
                classLoader = new URLClassLoader(classpath.toUrls());
                ignoreParent = true;
            }
            cachedGlobalBeanClassName = JkInternalClasspathScanner.of()
                    .findClassedExtending(classLoader, JkBean.class, path -> true, true, false);
            if (JkLog.isVerbose()) {
                JkLog.trace("All JkBean classes scanned in " + (System.currentTimeMillis() - t0) + " ms.");
                cachedGlobalBeanClassName.forEach(className -> JkLog.trace("  " + className));
            }
            storeGlobalKbeanClasses(cachedGlobalBeanClassName);
        }
        return cachedGlobalBeanClassName;
    }

    List<Class<? extends JkBean>> defBeanClasses() {
        List result = defBeanClassNames().stream()
                .sorted()
                .map(className -> JkClassLoader.ofCurrent().load(className))
                .collect(Collectors.toList());
        return result;
    }

    boolean hasDefSource() {
        if (!Files.exists(defSourceDir)) {
            return false;
        }
        return JkPathTree.of(defSourceDir).andMatching(true,
                "**.java", "*.java", "**.kt", "*.kt").count(0, false) > 0;
    }

    private List<String> defBeanClassNames() {
        if (cachedDefBeanClassNames == null) {
            long t0 = System.currentTimeMillis();
            ClassLoader classLoader = JkClassLoader.ofCurrent().get();
            boolean ignoreParent = false;
            if (classpath != null) {
                // If classpath is set, then sources has been compiled in work dir
                classLoader = new URLClassLoader(JkPathSequence.of().and(this.defClassDir).toUrls());
                ignoreParent = true;
            }
            cachedDefBeanClassNames = JkInternalClasspathScanner.of().findClassedExtending(classLoader,
                    JkBean.class, EngineBeanClassResolver::scan, true, ignoreParent);
            if (JkLog.isVerbose()) {
                JkLog.trace("Def JkBean classes scanned in " + (System.currentTimeMillis() - t0) + " ms.");
                cachedDefBeanClassNames.forEach(className -> JkLog.trace("  " + className ));
            }
        }
        return cachedDefBeanClassNames;
    }

    private static boolean scan(String pathElement) {
        return !pathElement.startsWith(JAVA_HOME);  // Don't scan jre classes
        //return true;
    }

    private EngineCommand toEngineCommand(CommandLine.JkBeanAction action,
                                                 Map<String, Class<? extends JkBean>> beanClasses) {
        Class<? extends JkBean> beanClass = (action.beanName == null)
                ? beanClasses.get(null)
                : getJkBeanClass(beanClasses.values(), action.beanName);
        return new EngineCommand(action.action, beanClass, action.member, action.value);
    }

    private  Class<? extends JkBean> getJkBeanClass(Collection<Class<? extends JkBean>> beanClasses, String name) {
        return beanClasses.stream()
                .filter(Objects::nonNull)
                .filter(beanClass -> JkBean.nameMatches(beanClass.getName(), name))
                .findFirst()
                .orElseThrow(() -> beanClassNotFound(name));
    }

    private static List<String> findClassesMatchingName(List<String> beanClassNameCandidates, String name) {
        return beanClassNameCandidates.stream()
                .filter(className -> JkBean.nameMatches(className, name))
                .collect(Collectors.toList());
    }

    private JkPathTree defSources() {
        return JkPathTree.of(this.defSourceDir).withMatcher(Engine.JAVA_OR_KOTLIN_SOURCE_MATCHER);
    }

    private void storeGlobalKbeanClasses(List<String> classNames) {
        Path store = baseDir.resolve(JkConstants.WORK_PATH).resolve(CACHE_FILENAME);
        if (!Files.exists(store.getParent().getParent())) {
            return;
        }
        String content = String.join(System.lineSeparator(), classNames);
        JkPathFile.of(store).createIfNotExist().write(content.getBytes(StandardCharsets.UTF_8));
    }

    List<String> readKbeanClasses() {
        Path store = baseDir.resolve(JkConstants.WORK_PATH).resolve(CACHE_FILENAME);
        if (!Files.exists(store)) {
            return Collections.emptyList();
        }
        return JkUtilsPath.readAllLines(store);
    }

}
