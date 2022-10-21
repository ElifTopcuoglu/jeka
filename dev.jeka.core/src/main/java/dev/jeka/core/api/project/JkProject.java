package dev.jeka.core.api.project;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.depmanagement.artifact.JkArtifactId;
import dev.jeka.core.api.depmanagement.artifact.JkStandardFileArtifactProducer;
import dev.jeka.core.api.depmanagement.publication.JkIvyPublication;
import dev.jeka.core.api.depmanagement.publication.JkMavenPublication;
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.resolution.JkResolveResult;
import dev.jeka.core.api.depmanagement.resolution.JkResolvedDependencyNode;
import dev.jeka.core.api.java.JkJavaCompiler;
import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.tool.JkConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Stands for the whole project model for building purpose. It has the same purpose and scope than the Maven _POM_ but
 * it also holds methods to actually perform builds.
 *
 * A complete overview is available <a href="https://jeka-dev.github.io/jeka/reference-guide/build-library-project-build/#project-api">here</a>.
 * <p/>
 *
 * A <code>JkProject</code> consists in 4 parts : <ul>
 *    <li>{@link JkProjectCompilation} : responsible to generate compile prod sources</li>
 *    <li>{@link JkProjectTesting} : responsible to compile test sources and run tests </li>
 *    <li>{@link JkProjectPackaging} : responsible to creates javadoc, sources and jars</li>
 *    <li>{@link JkProjectPublication} : responsible to publish the artifacts on binary repositories (Maven or Ivy)</li>
 * </ul>
 * Each of these parts are optional. This mean that you don't have to set the publication part if the project is not
 * supposed to be published. Or you don't have to set the <i>construction</i> part if the project publishes artifacts
 * that are created by other means.
 * <p>
 * {@link JkProject} defines <i>base</i> and <i>output</i> directories as they are shared with the 3 parts.
 */
public class JkProject implements JkIdeSupport.JkSupplier {

    public static final JkArtifactId SOURCES_ARTIFACT_ID = JkArtifactId.of("sources", "jar");

    public static final JkArtifactId JAVADOC_ARTIFACT_ID = JkArtifactId.of("javadoc", "jar");

    private static final JkJavaVersion DEFAULT_JAVA_VERSION = JkJavaVersion.V17;

    private static final String DEFAULT_ENCODING = "UTF-8";

    private Path baseDir = Paths.get("");

    private String outputDir = "jeka/output";

    private JkJavaVersion jvmTargetVersion = DEFAULT_JAVA_VERSION;

    private String sourceEncoding = DEFAULT_ENCODING;

    private final JkStandardFileArtifactProducer<JkProject> artifactProducer =
            JkStandardFileArtifactProducer.ofParent(this).setArtifactFilenameComputation(this::getArtifactPath);

    private JkCoordinate.ConflictStrategy duplicateConflictStrategy = JkCoordinate.ConflictStrategy.FAIL;

    private final JkDependencyResolver<JkProject> dependencyResolver;

    private final JkJavaCompiler<JkProject> compiler;

    private final JkProjectPackaging packaging;

    private final JkProjectPublication publication;

    private final JkProjectCompilation<JkProject> compilation;

    private final JkProjectTesting testing;


    private boolean includeTextAndLocalDependencies = true;

    private LocalAndTxtDependencies cachedTextAndLocalDeps;

    // For testing purpose
    URL dependencyTxtUrl;

    public Function<JkIdeSupport, JkIdeSupport> ideSupportModifier = x -> x;


    private JkProject() {
        compiler = JkJavaCompiler.ofParent(this);
        compilation = JkProjectCompilation.ofProd(this);
        testing = new JkProjectTesting(this);
        packaging = new JkProjectPackaging(this);
        dependencyResolver = JkDependencyResolver.ofParent(this)
                .addRepos(JkRepo.ofLocal(), JkRepo.ofMavenCentral())
                .setUseCache(true);
        registerArtifacts();
        publication = new JkProjectPublication(this);
    }

    public static JkProject of() {
        return new JkProject();
    }

    public JkProject apply(Consumer<JkProject> projectConsumer) {
        projectConsumer.accept(this);
        return this;
    }

    public JkProjectFlatFacade flatFacade() {
        return new JkProjectFlatFacade(this);
    }

    // ---------------------------- Getters / setters --------------------------------------------

    public Path getBaseDir() {
        return this.baseDir;
    }

    public JkProject setBaseDir(Path baseDir) {
        this.baseDir = JkUtilsPath.relativizeFromWorkingDir(baseDir);
        return this;
    }

    /**
     * Returns path of the directory under which are produced build files
     */
    public Path getOutputDir() {
        return baseDir.resolve(outputDir);
    }

    /**
     * Sets the output path dir relative to base dir.
     */
    public JkProject setOutputDir(String relativePath) {
        this.outputDir = relativePath;
        return this;
    }

    public JkJavaVersion getJvmTargetVersion() {
        return jvmTargetVersion;
    }

    public JkProject setJvmTargetVersion(JkJavaVersion jvmTargetVersion) {
        this.jvmTargetVersion = jvmTargetVersion;
        return this;
    }

    public String getSourceEncoding() {
        return sourceEncoding;
    }

    public JkProject setSourceEncoding(String sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
        return this;
    }

    public JkCoordinate.ConflictStrategy getDuplicateConflictStrategy() {
        return duplicateConflictStrategy;
    }

    public JkProject setDuplicateConflictStrategy(JkCoordinate.ConflictStrategy duplicateConflictStrategy) {
        this.duplicateConflictStrategy = duplicateConflictStrategy;
        return this;
    }

    /**
     * Returns the compiler compiling Java sources for this project. The returned instance is mutable
     * so users can modify it from this method return.
     */
    public JkJavaCompiler<JkProject> getCompiler() {
        return compiler;
    }

    public JkProjectCompilation<JkProject> getCompilation() {
        return compilation;
    }

    public JkProjectTesting getTesting() {
        return testing;
    }

    public JkProjectPublication getPublication() {
        return publication;
    }

    public JkProjectPackaging getPackaging() {
        return packaging;
    }

    public JkDependencyResolver<JkProject> getDependencyResolver() {
        return dependencyResolver;
    }

    public JkStandardFileArtifactProducer<JkProject> getArtifactProducer() {
        return artifactProducer;
    }


    // -------------------------- Other -------------------------

    @Override
    public String toString() {
        return "project " + getBaseDir().getFileName();
    }

    public String getInfo() {
        JkDependencySet compileDependencies = compilation.getDependencies();
        JkDependencySet runtimeDependencies = packaging.getRuntimeDependencies();
        JkDependencySet testDependencies = testing.getCompilation().getDependencies();
        StringBuilder builder = new StringBuilder("Project Location : " + this.getBaseDir() + "\n")
            .append("Production sources : " + compilation.getLayout().getInfo()).append("\n")
            .append("Test sources : " + testing.getCompilation().getLayout().getInfo()).append("\n")
            .append("Java Source Version : " + jvmTargetVersion + "\n")
            .append("Source Encoding : " + sourceEncoding + "\n")
            .append("Source file count : " + compilation.getLayout().resolveSources()
                    .count(Integer.MAX_VALUE, false) + "\n")
            .append("Download Repositories : " + dependencyResolver.getRepos() + "\n")
            .append("Declared Compile Dependencies : " + compileDependencies.getEntries().size() + " elements.\n");
        compileDependencies.getVersionedDependencies().forEach(dep -> builder.append("  " + dep + "\n"));
        builder.append("Declared Runtime Dependencies : " + runtimeDependencies
                .getEntries().size() + " elements.\n");
        runtimeDependencies.getVersionedDependencies().forEach(dep -> builder.append("  " + dep + "\n"));
        builder.append("Declared Test Dependencies : " + testDependencies.getEntries().size() + " elements.\n");
        testDependencies.getVersionedDependencies().forEach(dep -> builder.append("  " + dep + "\n"));
        builder.append("Defined Artifacts : " + artifactProducer.getArtifactIds());
        JkMavenPublication mavenPublication = publication.getMaven();
        if (mavenPublication.getModuleId() != null) {
            builder
                .append("Publish Maven repositories : " + mavenPublication.getPublishRepos()  + "\n")
                .append("Published Maven Module & version : " +
                        mavenPublication.getModuleId().toCoordinate(mavenPublication.getVersion()) + "\n")
                .append("Published Maven Dependencies :");
            mavenPublication.getDependencies().getEntries().forEach(dep -> builder.append("\n  " + dep));
        }
        JkIvyPublication ivyPublication = publication.getIvy();
        if (ivyPublication.getModuleId() != null) {
            builder
                    .append("Publish Ivy repositories : " + ivyPublication.getRepos()  + "\n")
                    .append("Published Ivy Module & version : " +
                            ivyPublication.getModuleId().toCoordinate(mavenPublication.getVersion()) + "\n")
                    .append("Published Ivy Dependencies :");
            ivyPublication.getDependencies().getEntries().forEach(dep -> builder.append("\n  " + dep));
        }
        return builder.toString();
    }

    @Override
    public JkIdeSupport getJavaIdeSupport() {
        JkQualifiedDependencySet qualifiedDependencies = JkQualifiedDependencySet.computeIdeDependencies(
                compilation.getDependencies(),
                packaging.getRuntimeDependencies(),
                testing.getCompilation().getDependencies(),
                JkCoordinate.ConflictStrategy.TAKE_FIRST);
        JkIdeSupport ideSupport = JkIdeSupport.of(baseDir)
            .setSourceVersion(jvmTargetVersion)
            .setProdLayout(compilation.getLayout())
            .setTestLayout(testing.getCompilation().getLayout())
            .setGeneratedSourceDirs(compilation.getGeneratedSourceDirs())
            .setDependencies(qualifiedDependencies)
            .setDependencyResolver(dependencyResolver);
        return ideSupportModifier.apply(ideSupport);
    }

    public void setJavaIdeSupport(Function<JkIdeSupport, JkIdeSupport> ideSupport) {
        this.ideSupportModifier = ideSupportModifier.andThen(ideSupport);
    }

    public JkLocalProjectDependency toDependency() {
        return toDependency(artifactProducer.getMainArtifactId(), null);
    }

    public JkLocalProjectDependency toDependency(JkTransitivity transitivity) {
        return toDependency(artifactProducer.getMainArtifactId(), transitivity);
    }

    public JkLocalProjectDependency toDependency(JkArtifactId artifactId, JkTransitivity transitivity) {
       Runnable maker = () -> artifactProducer.makeArtifact(artifactId);
       Path artifactPath = artifactProducer.getArtifactPath(artifactId);
       JkDependencySet exportedDependencies = compilation.getDependencies()
               .merge(packaging.getRuntimeDependencies()).getResult();
        return JkLocalProjectDependency.of(maker, artifactPath, this.baseDir, exportedDependencies)
                .withTransitivity(transitivity);
    }

    private Path getArtifactPath(JkArtifactId artifactId) {
        JkModuleId jkModuleId = publication.getModuleId();
        String fileBaseName = jkModuleId != null ? jkModuleId.getDotNotation()
                : baseDir.toAbsolutePath().getFileName().toString();
        return baseDir.resolve(outputDir).resolve(artifactId.toFileName(fileBaseName));
    }

    /**
     * Shorthand to build all missing artifacts for publication.
     */
    public void pack() {
        artifactProducer.makeAllMissingArtifacts();
    }

    private void registerArtifacts() {
        artifactProducer.putMainArtifact(packaging::createBinJar);
        artifactProducer.putArtifact(SOURCES_ARTIFACT_ID, packaging::createSourceJar);
        artifactProducer.putArtifact(JAVADOC_ARTIFACT_ID, packaging::createJavadocJar);
    }

    /**
     * Specifies if Javadoc and sources jars should be included in pack/publish. Default is true;
     */
    public JkProject includeJavadocAndSources(boolean includeJavaDoc, boolean includeSources) {
        if (includeJavaDoc) {
            artifactProducer.putArtifact(JAVADOC_ARTIFACT_ID, packaging::createJavadocJar);
        } else {
            artifactProducer.removeArtifact(JAVADOC_ARTIFACT_ID);
        }
        if (includeSources) {
            artifactProducer.putArtifact(SOURCES_ARTIFACT_ID, packaging::createSourceJar);
        } else {
            artifactProducer.removeArtifact(SOURCES_ARTIFACT_ID);
        }
        return this;
    }

    public boolean isIncludeTextAndLocalDependencies() {
        return includeTextAndLocalDependencies;
    }

    public JkProject setIncludeTextAndLocalDependencies(boolean includeTextAndLocalDependencies) {
        this.includeTextAndLocalDependencies = includeTextAndLocalDependencies;
        return this;
    }

     LocalAndTxtDependencies textAndLocalDeps() {
        if (cachedTextAndLocalDeps != null) {
            return cachedTextAndLocalDeps;
        }
        LocalAndTxtDependencies localDeps = LocalAndTxtDependencies.ofLocal(
                baseDir.resolve(JkConstants.JEKA_DIR + "/libs"));
        LocalAndTxtDependencies textDeps = dependencyTxtUrl == null
                ? LocalAndTxtDependencies.ofTextDescriptionIfExist(
                baseDir.resolve(JkConstants.JEKA_DIR + "/libs/dependencies.txt"))
                : LocalAndTxtDependencies.ofTextDescription(dependencyTxtUrl);
        cachedTextAndLocalDeps = localDeps.and(textDeps);
        return cachedTextAndLocalDeps;
    }

    public Document getDependenciesAsXml()  {
        Document document;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Element root = document.createElement("dependencies");
        document.appendChild(root);
        root.appendChild(xmlDeps(document, "compile", compilation.getDependencies()));
        root.appendChild(xmlDeps(document, "runtime", packaging.getRuntimeDependencies()));
        root.appendChild(xmlDeps(document, "test", testing.getCompilation().getDependencies()));
        return document;
    }

    private Element xmlDeps(Document document, String purpose, JkDependencySet deps) {
        JkResolveResult resolveResult = dependencyResolver.resolve(deps);
        JkResolvedDependencyNode tree = resolveResult.getDependencyTree();
        Element element = tree.toDomElement(document, true);
        element.setAttribute("purpose", purpose);
        return element;
    }


}