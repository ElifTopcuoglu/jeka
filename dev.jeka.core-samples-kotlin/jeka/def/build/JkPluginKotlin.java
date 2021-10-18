package build;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkVersionProvider;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.java.project.JkJavaProjectCompilation;
import dev.jeka.core.api.kotlin.JkKotlinCompiler;
import dev.jeka.core.api.kotlin.JkKotlinJvmCompileSpec;
import dev.jeka.core.api.kotlin.JkKotlinModules;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.*;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

import java.util.List;

import static dev.jeka.core.api.java.project.JkJavaProjectCompilation.JAVA_SOURCES_COMPILE_ACTION;

@JkDefClasspath("org.jetbrains.kotlin:kotlin-compiler:1.5.31")
public class JkPluginKotlin extends JkPlugin {

    public static final String KOTLIN_SOURCES_COMPILE_ACTION = "kotlin-sources-compile";

    // used for kotlin-JVM
    private JkJvm jvm;

    private JKCommon common;

    public boolean addStdlib = true;

    public String kotlinVersion;

    protected JkPluginKotlin(JkClass jkClass) {
        super(jkClass);
        kotlinVersion = JkOptions.get(JkKotlinCompiler.KOTLIN_VERSION_OPTION);
    }

    public final JkJvm jvm() {
        if (jvm == null) {
            jvm = setupKotlinJvm();
        }
        return jvm;
    }

    public final JKCommon common() {
        if (common == null) {
            common = new JKCommon();
        }
        return common;
    }

    @JkDoc("Displays loaded compiler plugins and options")
    public void showPlugins() {
        if (jvm != null) {
            JkLog.info("Options for declared external plugins in Kotlin-jvm compiler :");
            List<String> options = jvm.kotlinCompiler.getPlugins();
            if (options.isEmpty()) {
                JkLog.info("No compiler plugin or option defined.");
            } else {
                options.forEach(option -> JkLog.info(option));
            }
        }
    }

    @Override
    protected void afterSetup() throws Exception {
        if (common != null) {
            common.setupJvmProject(jvm());
        }
    }

    private JkJvm setupKotlinJvm() {
        JkPluginJava javaPlugin = this.getJkClass().getPlugin(JkPluginJava.class);
        JkJavaProject javaProject = javaPlugin.getProject();
        final JkKotlinCompiler kotlinCompiler;
        if (JkUtilsString.isBlank(kotlinVersion)) {
            kotlinCompiler = JkKotlinCompiler.ofKotlinHomeCommand("kotlinc");
            JkLog.warn("No version of kotlin has been specified, will use the version installed on KOTLIN_HOME : "
                    + kotlinCompiler.getVersion());
        } else {
            kotlinCompiler = JkKotlinCompiler.ofJvm(javaProject.getConstruction().getDependencyResolver().getRepos(),
                    kotlinVersion);
        }
        kotlinCompiler.setLogOutput(true);
        String effectiveVersion = kotlinCompiler.getVersion();
        JkJavaProjectCompilation<?> prodCompile = javaProject.getConstruction().getCompilation();
        JkJavaProjectCompilation<?> testCompile = javaProject.getConstruction().getTesting().getCompilation();
        prodCompile.getPreCompileActions().appendBefore(KOTLIN_SOURCES_COMPILE_ACTION, JAVA_SOURCES_COMPILE_ACTION,
                () -> compileKotlin(kotlinCompiler, javaProject));
        testCompile.getPreCompileActions().appendBefore(KOTLIN_SOURCES_COMPILE_ACTION, JAVA_SOURCES_COMPILE_ACTION,
                () -> compileTestKotlin(kotlinCompiler, javaProject));
        JkVersionProvider versionProvider = JkKotlinModules.versionProvider(effectiveVersion);
        prodCompile.setDependencies(deps -> deps.andVersionProvider(versionProvider));
        if (addStdlib) {
            if (kotlinCompiler.isProvidedCompiler()) {
                prodCompile.setDependencies(deps -> deps.andFiles(kotlinCompiler.getStdLib()));
            } else {
                prodCompile.setDependencies(deps -> deps
                        .and(JkKotlinModules.STDLIB_JDK8)
                        .and(JkKotlinModules.REFLECT));
                testCompile.setDependencies(deps -> deps.and(JkKotlinModules.TEST));
            }
        }
        return new JkJvm(javaProject, kotlinCompiler);
    }

    private void compileKotlin(JkKotlinCompiler kotlinCompiler, JkJavaProject javaProject) {
        JkJavaProjectCompilation compilation = javaProject.getConstruction().getCompilation();
        JkKotlinJvmCompileSpec compileSpec = JkKotlinJvmCompileSpec.of()
                .setClasspath(compilation.resolveDependencies().getFiles())
                .setOutputDir(compilation.getLayout().getOutputDir().resolve("classes"))
                .setTargetVersion(javaProject.getConstruction().getJvmTargetVersion())
                .addSources(compilation.getLayout().resolveSources());
        kotlinCompiler.compile(compileSpec);
    }

    private void compileTestKotlin(JkKotlinCompiler kotlinCompiler, JkJavaProject javaProject) {
        JkJavaProjectCompilation compilation = javaProject.getConstruction().getTesting().getCompilation();
        JkKotlinJvmCompileSpec compileSpec = JkKotlinJvmCompileSpec.of()
                .setClasspath(compilation.resolveDependencies().getFiles()
                        .and(javaProject.getConstruction().getCompilation().getLayout().getClassDirPath()))
                .setOutputDir(compilation.getLayout().getOutputDir().resolve("test-classes"))
                .setTargetVersion(javaProject.getConstruction().getJvmTargetVersion())
                .addSources(compilation.getLayout().resolveSources());
        kotlinCompiler.compile(compileSpec);
    }

    public JkArtifactId addFatJar(String classifier) {
        JkArtifactId artifactId = JkArtifactId.of(classifier, "jar");
        this.jvm.project.getPublication().getArtifactProducer()
                .putArtifact(artifactId,
                        path -> jvm.project.getConstruction().createFatJar(path));
        return artifactId;
    }

    public static class JkJvm {

        private final JkJavaProject project;

        private final JkKotlinCompiler kotlinCompiler;

        public JkJvm(JkJavaProject project, JkKotlinCompiler kotlinCompiler) {
            this.project = project;
            this.kotlinCompiler = kotlinCompiler;
        }

        public JkJavaProject getProject() {
            return project;
        }

        public JkKotlinCompiler getKotlinCompiler() {
            return kotlinCompiler;
        }

        public JkJvm useFatJarForMainArtifact() {
            project.getPublication().getArtifactProducer()
                    .putArtifact(JkArtifactId.ofMainArtifact("jar"),
                            path -> project.getConstruction().createFatJar(path));
            return this;
        }
    }


    public static class JKCommon {

        private String srcDir = "src/main/kotlin-common";

        private String testDir = "src/test/kotlin-dir";

        private JkDependencySet compileDependencies = JkDependencySet.of();

        private JkDependencySet testDependencies = JkDependencySet.of();

        private boolean addCommonStdLibs = true;

        private JKCommon() {}

        private void setupJvmProject(JkJvm jvm) {
            JkJavaProjectCompilation<?> prodCompile = jvm.project.getConstruction().getCompilation();
            JkJavaProjectCompilation<?> testCompile = jvm.project.getConstruction().getTesting().getCompilation();
            prodCompile.getLayout().addSource(srcDir);
            if (testDir != null) {
                testCompile.getLayout().addSource(testDir);
                if (addCommonStdLibs) {
                    testCompile.setDependencies(deps -> deps
                            .and(JkKotlinModules.TEST_COMMON)
                            .and(JkKotlinModules.TEST_ANNOTATIONS_COMMON)
                    );
                }
            }
            prodCompile.setDependencies(deps -> deps.and(compileDependencies));
            testCompile.setDependencies(deps -> deps.and(testDependencies));
        }

        public String getSrcDir() {
            return srcDir;
        }

        public JKCommon setSrcDir(String srcDir) {
            this.srcDir = srcDir;
            return this;
        }

        public String getTestDir() {
            return testDir;
        }

        public JKCommon setTestDir(String testDir) {
            this.testDir = testDir;
            return this;
        }

        public JkDependencySet getCompileDependencies() {
            return compileDependencies;
        }

        public JKCommon setCompileDependencies(JkDependencySet compileDependencies) {
            this.compileDependencies = compileDependencies;
            return this;
        }

        public JkDependencySet getTestDependencies() {
            return testDependencies;
        }

        public JKCommon setTestDependencies(JkDependencySet testDependencies) {
            this.testDependencies = testDependencies;
            return this;
        }

        public boolean isAddCommonStdLibs() {
            return addCommonStdLibs;
        }

        public JKCommon setAddCommonStdLibs(boolean addCommonStdLibs) {
            this.addCommonStdLibs = addCommonStdLibs;
            return this;
        }
    }

}
