package dev.jeka.core.api.java.project;

import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.file.JkResourceProcessor;
import dev.jeka.core.api.function.JkRunnables;
import dev.jeka.core.api.java.JkClasspath;
import dev.jeka.core.api.java.JkJavaCompileSpec;
import dev.jeka.core.api.java.JkJavaCompiler;
import dev.jeka.core.api.java.junit.JkTestProcessor;
import dev.jeka.core.api.java.junit.JkTestResult;
import dev.jeka.core.api.java.junit.JkTestSelection;
import dev.jeka.core.api.system.JkLog;

import java.nio.charset.Charset;
import java.nio.file.Path;

public class JkJavaProjectTestTasks {

    private final JkJavaProjectMaker maker;

    private final JkRunnables preTest = JkRunnables.of(() -> {});

    private final JkRunnables resourceGenerator = JkRunnables.of(() -> {});

    public final JkRunnables postTest = JkRunnables.of(() -> {});

    private final JkRunnables resourceProcessor;

    private final JkRunnables compileRunner;

    private JkTestProcessor testProcessor;

    public final JkRunnables testExecutor = JkRunnables.of(this::executeWithTestProcessor);

    private JkJavaCompiler compiler = JkJavaCompiler.ofJdk();

    private boolean done;

    private boolean skipTests;

    // ----- Junit5

    private boolean breakOnFailures = true;

    private final JkTestSelection testSelection;


    JkJavaProjectTestTasks(JkJavaProjectMaker maker, Charset charset) {
        this.maker = maker;
        resourceProcessor = JkRunnables.of(() -> JkResourceProcessor.of(maker.project.getSourceLayout().getTestResources())
                .and(maker.project.getResourceInterpolators())
                .generateTo(maker.getOutLayout().getTestClassDir(), charset));
        compileRunner = JkRunnables.of(() -> {
            final JkJavaCompileSpec testCompileSpec = getTestCompileSpec();
            compiler.compile(testCompileSpec);
        });
        testProcessor = defaultTestProcessor();
        testSelection = defaultTestSelection();
    }

    public JkRunnables getPreTest() {
        return preTest;
    }

    public JkRunnables getResourceGenerator() {
        return resourceGenerator;
    }

    public JkRunnables getResourceProcessor() {
        return resourceProcessor;
    }

    public JkRunnables getCompileRunner() {
        return compileRunner;
    }

    public JkJavaCompiler getCompiler() {
        return compiler;
    }

    public JkJavaProjectTestTasks setCompiler(JkJavaCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    public JkJavaProjectTestTasks setForkCompile(boolean fork, String ... params) {
        compiler = compiler.withForking(fork, params);
        return this;
    }

    private JkJavaCompileSpec getTestCompileSpec() {
        JkJavaCompileSpec result = maker.project.getCompileSpec().copy();
        final JkPathSequence classpath = maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_TEST).andPrepending(maker.getOutLayout().getClassDir());
        return result
                .setClasspath(classpath)
                .addSources(maker.project.getSourceLayout().getTests())
                .setOutputDir(maker.getOutLayout().getTestClassDir());
    }

    public JkClasspath getTestClasspath() {
        return JkClasspath.of(maker.getOutLayout().getTestClassDir())
                .and(maker.getOutLayout().getClassDir())
                .and(maker.fetchDependenciesFor(JkJavaDepScopes.SCOPES_FOR_TEST));
    }

    public boolean isTestSkipped() {
        return skipTests;
    }

    public void setSkipTests(boolean skipTests) {
        this.skipTests = skipTests;
    }

    /**
     * Performs entire test phase, including : <ul>
     *     <li>compile regular code if needed</li>
     *     <li>perform pre test tasks if present</li>
     *     <li>compile test code and process test resources</li>
     *     <li>execute compiled tests</li>
     *     <li>execute post tesks if present</li>
     * </ul>
     */
    public void run() {
        maker.getTasksForCompilation().runIfNecessary();
        JkLog.startTask("Running unit tests");
        if (maker.project.getSourceLayout().getTests().count(0, false) == 0) {
            JkLog.info("No unit test found in : " + maker.project.getSourceLayout().getTests());
        } else {
            this.maker.getTasksForCompilation().runIfNecessary();
            preTest.run();
            compileRunner.run();
            resourceGenerator.run();
            resourceProcessor.run();
            testExecutor.run();
            postTest.run();
        }
        JkLog.endTask();
    }

    /**
     * As #run but perfom only if not already done.
     */
    public void runIfNecessary() {
        if (done) {
            JkLog.trace("Test task already done. Won't perfom again.");
        } else if (skipTests) {
            JkLog.info("Tests are skipped. Won't perfom.");
        } else {
            run();
            done = true;
        }
    }

    void reset() {
        done = false;
    }


    public boolean isBreakOnFailures() {
        return breakOnFailures;
    }

    public JkJavaProjectTestTasks setBreakOnFailures(boolean breakOnFailures) {
        this.breakOnFailures = breakOnFailures;
        return this;
    }

    public JkTestSelection getTestSelection() {
        return testSelection;
    }

    public JkTestProcessor getTestProcessor() {
        return testProcessor;
    }

    private void executeWithTestProcessor() {
        JkTestResult result = testProcessor.launch(getTestClasspath(), testSelection);
        if (breakOnFailures) {
            result.assertNoFailure();
        }
    }

    private JkTestProcessor defaultTestProcessor() {
        JkTestProcessor result = JkTestProcessor.of();
        final Path reportDir = maker.getOutLayout().getTestReportDir().resolve("junit");
        result.getEngineBehavior()
                .setLegacyReportDir(reportDir)
                .setProgressDisplayer(JkTestProcessor.JkProgressOutputStyle.ONE_LINE);
        return result;
    }

    private JkTestSelection defaultTestSelection() {
        return JkTestSelection.ofStandard(maker.getOutLayout().getTestClassDir());
    }

}
