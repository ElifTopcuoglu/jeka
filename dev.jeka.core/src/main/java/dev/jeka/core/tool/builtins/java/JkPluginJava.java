package dev.jeka.core.tool.builtins.java;

import dev.jeka.core.api.crypto.gpg.JkGpg;
import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.java.*;
import dev.jeka.core.api.java.testing.JkTestProcessor;
import dev.jeka.core.api.java.project.*;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.system.JkProcess;
import dev.jeka.core.api.utils.JkUtilsIO;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.*;
import dev.jeka.core.tool.builtins.repos.JkPluginPgp;
import dev.jeka.core.tool.builtins.repos.JkPluginRepo;
import dev.jeka.core.tool.builtins.scaffold.JkPluginScaffold;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plugin for building Java projects. It comes with a {@link JkJavaProject} pre-configured with {@link JkOptions}.
 * and a decoration for scaffolding.
 */
@JkDoc("Build of a Java project through a JkJavaProject instance.")
@JkDocPluginDeps({JkPluginRepo.class, JkPluginScaffold.class})
public class JkPluginJava extends JkPlugin implements JkJavaIdeSupportSupplier {

    // ------------  Options injectable by command line --------------------------------

    /**
     * Options for the publish tasks. These options are injectable from command line.
     */
    public final JkPublishOptions publish = new JkPublishOptions();

    /**
     * Options for the packaging tasks (jar creation). These options are injectable from command line.
     */
    public final JkJavaPackOptions pack = new JkJavaPackOptions();

    /**
     * Options for the testing tasks. These options are injectable from command line.
     */
    public final JkTestOptions tests = new JkTestOptions();

    @JkDoc("Extra arguments to be passed to the compiler (e.g. -Xlint:unchecked).")
    public String compilerExtraArgs;

    // ----------------------------------------------------------------------------------

    private final JkPluginRepo repoPlugin;

    private final JkPluginScaffold scaffoldPlugin;

    private JkJavaProject project;

    protected JkPluginJava(JkCommandSet run) {
        super(run);
        this.scaffoldPlugin = run.getPlugins().get(JkPluginScaffold.class);
        this.repoPlugin = run.getPlugins().get(JkPluginRepo.class);

        // Pre-configure JkJavaProject instance
        this.project = JkJavaProject.ofMavenLayout(this.getCommandSet().getBaseDir());
        this.project.getDependencyManagement().addDependencies(JkDependencySet.ofLocal(run.getBaseDir().resolve(JkConstants.JEKA_DIR + "/libs")));
        final Path path = run.getBaseDir().resolve(JkConstants.JEKA_DIR + "/libs/dependencies.txt");
        if (Files.exists(path)) {
            this.project.getDependencyManagement().addDependencies(JkDependencySet.ofTextDescription(path));
        }
        this.project.getMaker().getOutputCleaner().set(() -> run.clean());
    }

    @JkDoc("Improves scaffolding by creating a project structure ready to build.")
    @Override  
    protected void activate() {
        this.applyOptionsToUnderlyingProject();
        this.setupScaffolder();
    }

    private void applyOptionsToUnderlyingProject() {
        JkVersionedModule versionedModule = project.getMaker().getSteps().getPublishing().getVersionedModule();
        if (versionedModule != null) {
            project.getMaker().getSteps().getPackaging().getManifest()
                    .addMainAttribute(JkManifest.IMPLEMENTATION_TITLE, versionedModule.getModuleId().getName())
                    .addMainAttribute(JkManifest.IMPLEMENTATION_VENDOR, versionedModule.getModuleId().getGroup())
                    .addMainAttribute(JkManifest.IMPLEMENTATION_VERSION, versionedModule.getVersion().getValue());
        }
        final JkJavaProjectMaker maker = project.getMaker();
        if (!pack.sources) {
            maker.removeArtifact(JkJavaProjectMaker.SOURCES_ARTIFACT_ID);
        }
        if (pack.javadoc) {
            maker.addJavadocArtifact();
        }
        if (pack.tests) {
            maker.addTestArtifact();
        }
        if (pack.testSources) {
            maker.addTestSourceArtifact();
        }
        JkJavaProjectMaker.JkSteps steps = maker.getSteps();
        if (steps.getCompilation().getCompiler().isDefault()) {  // If no compiler specified, try to set the best fitted
            steps.getCompilation().getCompiler().setForkingProcess(compilerProcess());
        }
        if (steps.getPublishing().getPublishRepos() == null
                || steps.getPublishing().getPublishRepos().getRepoList().isEmpty()) {
            steps.getPublishing().addPublishRepo(repoPlugin.publishRepository());
        }
        final JkRepo downloadRepo = repoPlugin.downloadRepository();
        project.getDependencyManagement().getResolver().addRepos(downloadRepo);
        if (pack.checksums().length > 0) {
            steps.getPackaging().setChecksumAlgorithms(pack.checksums());
        }
        JkPluginPgp pgpPlugin = this.getCommandSet().getPlugins().get(JkPluginPgp.class);
        JkGpg pgp = pgpPlugin.get();
        steps.getPublishing().setSigner(pgp.getSigner(pgpPlugin.keyName));

        JkTestProcessor testProcessor = steps.getTesting().getTestProcessor();
        if (tests.fork) {
            final JkJavaProcess javaProcess = JkJavaProcess.of().andCommandLine(this.tests.jvmOptions);
            testProcessor.setForkingProcess(javaProcess);
        }
        steps.getTesting().setSkipped(tests.skip);
        if (this.compilerExtraArgs != null) {
            steps.getCompilation().addOptions(JkUtilsString.translateCommandline(this.compilerExtraArgs));
        }
    }

    private void setupScaffolder() {
        String template = JkUtilsIO.read(JkPluginJava.class.getResource("buildclass.snippet"));
        String baseDirName = getCommandSet().getBaseDir().getFileName().toString();
        String code = template.replace("${group}", baseDirName).replace("${name}", baseDirName);
        JkLog.info("Create source directories.");
        project.getSourceLayout().getSources().getPathTrees().stream().forEach(tree -> tree.createIfNotExist());
        project.getSourceLayout().getResources().getPathTrees().stream().forEach(tree -> tree.createIfNotExist());
        project.getSourceLayout().getTests().getPathTrees().stream().forEach(tree -> tree.createIfNotExist());
        project.getSourceLayout().getTestResources().getPathTrees().stream().forEach(tree -> tree.createIfNotExist());
        scaffoldPlugin.getScaffolder().setCommandClassCode(code);
        scaffoldPlugin.getScaffolder().setClassFilename("Build.java");
    }

    //  ----------------------------- Shorthands ---------------------------------------

    /**
     * Cleans the output directory for the project
     */
    public JkPluginJava clean() {
        this.project.getMaker().clean();
        return this;
    }

    // ------------------------------ Accessors -----------------------------------------

    public JkJavaProject getProject() {
        return project;
    }

    public void setProject(JkJavaProject javaProject) {
        this.project = javaProject;
    }

    public JkPathTree ouputTree() {
        return JkPathTree.of(this.getProject().getMaker().getOutLayout().getOutputPath());
    }

    public JkPluginRepo getRepoPlugin() {
        return repoPlugin;
    }

    public  JkPluginScaffold getScaffoldPlugin() {
        return scaffoldPlugin;
    }

    // ------------------------------- command line methods -----------------------------

    @JkDoc("Performs compilation and resource processing.")
    public void compile() {
        project.getMaker().getSteps().getCompilation().run();
    }

    @JkDoc("Compiles and run tests defined within the project (typically Junit tests).")
    public void test() {
        project.getMaker().getSteps().getTesting().run();
    }

    @JkDoc("Generates from scratch artifacts defined through 'pack' options (Perform compilation and testing if needed).  " +
            "\nDoes not re-generate artifacts already generated : " +
            "execute 'clean java#pack' to re-generate artifacts.")
    public void pack() {
        project.getMaker().makeAllMissingArtifacts();
    }

    /**
     * Displays the resolved dependency tree on the console.
     */
    @JkDoc("Displays resolved dependency tree on console.")
    public final void showDependencies() {
        JkLog.info("Declared dependencies : ");
        project.getDependencyManagement().getDependencies().toResolvedModuleVersions().toList()
                .forEach(dep -> JkLog.info(dep.toString()));
        JkLog.info("Resolved to : ");
        final JkResolveResult resolveResult = this.getProject().getDependencyManagement().fetchDependencies();
        final JkDependencyNode tree = resolveResult.getDependencyTree();
        JkLog.info(String.join("\n", tree.toStrings()));
    }

    @JkDoc("Displays information about the Java project to build.")
    public void info() {
        JkLog.info(this.project.getInfo());
        JkLog.info("\nExecute 'java#showDependencies' to display details on dependencies.");

    }

    @JkDoc("Publishes produced artifacts to configured repository.")
    public void publish() {
        project.getMaker().getSteps().getPublishing().publish();
    }

    @JkDoc("Publishes produced artifacts to local repository.")
    public void publishLocal() {
        project.getMaker().getSteps().getPublishing().publishLocal();
    }

    @JkDoc("Fetches project dependencies in cache.")
    public void refreshDeps() {
        project.getDependencyManagement().fetchDependencies();
    }

    @Override
    public JkJavaIdeSupport getJavaIdeSupport() {
        return project.getJavaIdeSupport();
    }

    public static class JkPublishOptions {

        @JkDoc("If true, publishing will occur only in the local repository.")
        public boolean localOnly = false;

    }

    private JkProcess compilerProcess() {
        final Map<String, String> jdkOptions = JkOptions.getAllStartingWith("jdk.");
        JkJavaProjectMakerCompilationStep compilation = project.getMaker().getSteps().getCompilation();
        return JkJavaCompiler.getForkedProcessOnJavaSourceVersion(jdkOptions,
                compilation.getJavaVersion().get());
    }

}
