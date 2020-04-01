package dev.jeka.core.api.tooling.intellij;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkPopularModules;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.java.project.JkJavaIdeSupport;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.java.project.JkProjectSourceLayout;
import dev.jeka.core.api.tooling.eclipse.JkEclipseClasspathGeneratorTest;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by angibaudj on 21-09-17.
 */
public class JkImlGeneratorTest {

    @Test
    public void generate() throws Exception {
        final Path top = unzipToDir("sample-multi-scriptless.zip");
        final Path base = top.resolve("base");

        final JkProjectSourceLayout sourceLayout= JkProjectSourceLayout.ofSimpleStyle()
                .withResources("res").withTestResources("res-test").withBaseDir(base);
        final JkJavaProject baseProject = JkJavaProject.of(sourceLayout);
        baseProject.getDependencyManagement().addDependencies(JkDependencySet.of().and(JkPopularModules.APACHE_HTTP_CLIENT, "4.5.6"));
        final JkImlGenerator baseGenerator = JkImlGenerator.of(baseProject.getJavaIdeSupport());
        final String result0 = baseGenerator.generate();
        System.out.println("\nbase .classpath");
        System.out.println(result0);

        final Path core = top.resolve("core");
        final JkJavaProject coreProject = JkJavaProject.of(sourceLayout.withBaseDir(core));
        final JkDependencySet coreDeps = JkDependencySet.of().and(baseProject);
        coreProject.getDependencyManagement().addDependencies(coreDeps);
        coreProject.getMaker().getSteps().getTesting().getTestProcessor().setForkingProcess(true);
        final JkImlGenerator coreGenerator = JkImlGenerator.of(coreProject.getJavaIdeSupport());
        final String result1 = coreGenerator.generate();
        System.out.println("\ncore .classpath");
        System.out.println(result1);

        final Path desktop = top.resolve("desktop");
        final JkDependencySet deps = JkDependencySet.of().and(coreProject);
        final JkImlGenerator desktopGenerator = JkImlGenerator.of(JkJavaIdeSupport.ofDefault()
                .withSourceLayout(sourceLayout.withBaseDir(desktop))
                .withDependencies(deps)
                .withDependencyResolver(coreProject.getDependencyManagement().getResolver()));
        final String result2 = desktopGenerator.generate();

        System.out.println("\ndesktop .classpath");
        System.out.println(result2);

        final JkJavaProject desktopProject = JkJavaProject.of(sourceLayout.withBaseDir(desktop));
        desktopProject.getDependencyManagement().addDependencies(deps);
        desktopProject.getMaker().makeAllArtifacts();

        JkPathTree.of(top).deleteContent();
    }

    private static Path unzipToDir(String zipName) throws IOException, URISyntaxException {
        final Path dest = Files.createTempDirectory(JkEclipseClasspathGeneratorTest.class.getName());
        final Path zip = Paths.get(JkEclipseClasspathGeneratorTest.class.getResource(zipName).toURI());
        JkPathTree.ofZip(zip).copyTo(dest);
        System.out.println("unzipped in " + dest);
        return dest;
    }

}