package dev.jeka.core.api.testing;

import dev.jeka.core.api.depmanagement.JkModuleFileProxy;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.function.JkRunnables;
import dev.jeka.core.api.function.JkUnaryOperator;
import dev.jeka.core.api.java.JkClassLoader;
import dev.jeka.core.api.java.JkClasspath;
import dev.jeka.core.api.java.JkJavaProcess;
import dev.jeka.core.api.java.JkUrlClassLoader;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsPath;
import org.junit.platform.launcher.core.LauncherConfig;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Processor executing a given bunch of tests existing in compiled Java classes. <p/>
 * This relies on Junit-platform (junit5), but having Junit-platform libraries on the classpath is optional.
 * For most of the cases, processors can be used without coding against junit-platform API but, it is possible to use
 * directly Junit-platform API for fine-tuning.<p/>
 * Users can configure <ul>
 *     <li>Runtime support : classpath, forked process</li>
 *     <li>Engine behavior : listeners, reports, progress display </li>
 *     <li>The tests to run: discovery, selectors, filters, ...</li>
 * </ul>
 */
public final class JkTestProcessor<T> {

    /**
     * Style of progress mark to display on console while the tests are running.
     */
    public enum JkProgressOutputStyle implements Serializable {
        FULL, TREE, ONE_LINE, SILENT, BAR
    }

    private static final String JAR_LOCATION = "META-INF/junitplatform/";

    private static final String ENGINE_SERVICE = "org.junit.platform.engine.TestEngine";

    private static final String PLATFORM_LAUNCHER_CLASS_NAME = "org.junit.platform.launcher.Launcher";

    private static final String PLATFORM_COMMONS_CLASS_NAME = "org.junit.platform.commons.util.ClassNamePatternFilterUtils";

    // This class is absent from platform-engine 1.5.2,
    // so if 1.5.2 is present inh the classpath, we need to add 1.6 as well.
    private static final String PLATFORM_ENGINE_CLASS_NAME = "org.junit.platform.engine.EngineDiscoveryListener";

    private static final String PLATFORM_REPORT_CLASS_NAME =
            "org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener";

    private static final String OPENTEST4J_CLASS_NAME = "org.opentest4j.ValueWrapper";

    private static final String JUNIT_PLATFORM_LAUNCHER_MODULE = "org.junit.platform:junit-platform-launcher";

    private static final String JUNIT_PLATFORM_REPORTING_MODULE = "org.junit.platform:junit-platform-reporting";

    private JkJavaProcess forkingProcess = JkJavaProcess.ofJava(JkTestProcessor.class.getName());  // Tests are forked by default

    private JkEngineBehavior<T> engineBehavior;

    private final JkRunnables preActions = JkRunnables.of();

    private final JkRunnables postActions = JkRunnables.of();

    private String junitPlatformVersion = "1.8.2";

    private Supplier<JkRepoSet> repoSetSupplier;

    /**
     * For parent chaining
     */
    public final transient T __;

    private JkTestProcessor(T __) {
        this.__ = __;
    }

    public static JkTestProcessor<Void> of() {
        return ofParent(null);
    }

    public static <T> JkTestProcessor<T> ofParent(T parent) {
        JkTestProcessor<T> result = new JkTestProcessor<T>(parent);
        result.engineBehavior = new JkEngineBehavior(result);
        return result;
    }

    public JkJavaProcess getForkingProcess() {
        return forkingProcess;
    }

    public JkEngineBehavior<T> getEngineBehavior() {
        return engineBehavior;
    }

    public JkRunnables getPreActions() {
        return preActions;
    }

    public JkRunnables getPostActions() {
        return postActions;
    }

    public JkTestProcessor<T> setForkingProcess(JkJavaProcess process) {
        this.forkingProcess = process;
        return this;
    }

    public JkTestProcessor<T> setForkingProcess(boolean fork) {
        if (fork) {
            if (forkingProcess != null) {
                return this;
            }
            this.forkingProcess = JkJavaProcess.ofJava(JkTestProcessor.class.getName());
        } else {
            forkingProcess = null;
        }
        return this;
    }

    public String getJunitPlatformVersion() {
        return junitPlatformVersion;
    }

    public JkTestProcessor<T> setJunitPlatformVersion(String junitPlatformVersion) {
        this.junitPlatformVersion = junitPlatformVersion;
        return this;
    }

    public JkTestProcessor<T> setRepoSetSupplier(Supplier<JkRepoSet> repoSetSupplier) {
        this.repoSetSupplier = repoSetSupplier;
        return this;
    }

    private List<Path> computeClasspath(JkPathSequence testClasspath) {
        JkClasspath result = JkClasspath.of(testClasspath);
        JkClassLoader classloader = JkClassLoader.ofCurrent();
        result = addIfNeeded(result, classloader, PLATFORM_LAUNCHER_CLASS_NAME, JUNIT_PLATFORM_LAUNCHER_MODULE);
        result = addIfNeeded(result, classloader, PLATFORM_REPORT_CLASS_NAME, JUNIT_PLATFORM_REPORTING_MODULE);
        JkUrlClassLoader ucl = JkUrlClassLoader.of(result, classloader.get());
        Class<?> testEngineClass = ucl.toJkClassLoader().load(ENGINE_SERVICE);
        return result.getEntries();
    }

    private JkClasspath addIfNeeded(JkClasspath classpath, JkClassLoader classloader,
                                           String className, String moduleName) {
        JkClasspath result = classpath;
        if (!classloader.isDefined(className)) {
            if (result.getEntryContainingClass(className) == null) {
                String dep = moduleName + ":" + this.junitPlatformVersion;
                Path path = JkModuleFileProxy.of(this.repoSetSupplier.get(), dep).get();
                result = result.and(path);
            }
        }
        return result;
    }

    /**
     * Launches the specified test set with the underlying junit-platform. The classloader running the tests includes
     * the classpath of the current classloader plus the specified one.
     */
    public JkTestResult launch(JkPathSequence extraTestClasspath, JkTestSelection testSelection) {
        if (!testSelection.hasTestClasses()) {
            JkLog.trace("No test class found in %s. No test to run." , testSelection.getTestClassRoots() );
            return JkTestResult.of();
        }
        final JkTestResult result;
        preActions.run();
        if (forkingProcess == null) {
            JkLog.startTask("Executing tests");
            result = launchInClassloader(extraTestClasspath, testSelection);
        } else {
            JkLog.startTask("Execute tests in forked process");
            result = launchInForkedProcess(extraTestClasspath, testSelection);
        }
        postActions.run();
        JkLog.info("Result : " + result.getTestCount());
        JkLog.endTask();
        return result;
    }

    private JkTestResult launchInClassloader(JkPathSequence testClasspath, JkTestSelection testSelection) {
        List<Path> classpath = computeClasspath(testClasspath);
        return JkInternalJunitDoer.instance(classpath).launch(engineBehavior, testSelection);
    }

    private JkTestResult launchInForkedProcess(JkPathSequence testClasspath, JkTestSelection testSelection) {
        Path serializedResultPath = JkUtilsPath.createTempFile("testResult-", ".ser");
        Args args = new Args();
        args.resultFile = serializedResultPath.toAbsolutePath().toString();
        args.engineBehavior = this.engineBehavior;
        args.testSelection = testSelection;
        Path serializedArgPath = JkUtilsPath.createTempFile("testArgs-", ".ser");
        JkUtilsIO.serialize(args, serializedArgPath);
        String arg = serializedArgPath.toAbsolutePath().toString();
        List<Path> classpath = JkClassLoader.ofCurrent().getClasspath()
                .andPrepend(computeClasspath(testClasspath)).withoutDuplicates().getEntries();
        forkingProcess.copy()
                .setLogCommand(JkLog.isVerbose())
                .setFailOnError(true)
                .setClasspath(classpath)
                .addParams(arg)
                .exec();
        JkUtilsPath.deleteFile(serializedArgPath);
        JkTestResult result = JkUtilsIO.deserialize(serializedResultPath);
        JkUtilsPath.deleteFile(serializedResultPath);
        return result;
    }

    /**
     * Non public API. Used by #launchInClassloader.
     */
    public static void main(String[] args) {
        Path argFile = Paths.get(args[0]);
        Args data = JkUtilsIO.deserialize(argFile);
        JkTestResult result =
                JkInternalJunitDoer.instance(Collections.emptyList()).launch(data.engineBehavior, data.testSelection);
        JkUtilsIO.serialize(result, Paths.get(data.resultFile));
        System.exit(0);  // Triggers shutdown hooks
    }

    private static class Args implements Serializable {

        private static final long serialVersionUID = 1L;

        JkEngineBehavior engineBehavior;

        String resultFile;

        JkTestSelection testSelection;

    }

    public static class JkEngineBehavior<T> implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Parent chaining.
         */
        public final transient JkTestProcessor<T> __;

        private String legacyReportDir; // Use String instead of Path for serialisation

        private JkProgressOutputStyle progressDisplayer;

        private JkUnaryOperator<LauncherConfig.Builder> launcherConfigurer;

        private JkEngineBehavior(JkTestProcessor<T> __) {
            this.__ = __;
        }

        public Path getLegacyReportDir() {
            return legacyReportDir == null ? null : Paths.get(legacyReportDir);
        }

        public JkProgressOutputStyle getProgressDisplayer() {
            return progressDisplayer;
        }

        public JkUnaryOperator<LauncherConfig.Builder> getLauncherConfigurer() {
            return launcherConfigurer;
        }

        /**
         * Sets the directory where will be generated the legacy standard XML report.
         * If {@code null}, no legacy standard XML report will be generated.
         */
        public JkEngineBehavior<T> setLegacyReportDir(Path legacyReportDir) {
            this.legacyReportDir = legacyReportDir == null ? null : legacyReportDir.toString();
            return this;
        }

        /**
         * Sets the test progress type to display on the console.
         * If {@code null}, no progress will be displayed.
         */
        public JkEngineBehavior<T> setProgressDisplayer(JkProgressOutputStyle progressDisplayer) {
            this.progressDisplayer = progressDisplayer;
            return this;
        }

        /**
         * Lets to setup the Junit-platform native {@link LauncherConfig} used to build
         * the {@link org.junit.platform.launcher.Launcher}.
         * @param launcherConfigurer a function that takes the default {@link LauncherConfig} as argument
         *                          and returns the config to use.
         */
        public JkEngineBehavior<T> setLauncherConfigurer(JkUnaryOperator<LauncherConfig.Builder> launcherConfigurer) {
            this.launcherConfigurer = launcherConfigurer;
            return this;
        }

    }

}
