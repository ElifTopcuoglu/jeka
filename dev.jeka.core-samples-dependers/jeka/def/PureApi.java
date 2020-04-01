import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkJavaDepScopes;
import dev.jeka.core.api.java.project.JkJavaProject;
import dev.jeka.core.api.system.JkLog;

public class PureApi {

    public static void main(String[] args) {
        JkLog.setHierarchicalConsoleConsumer();  // activate console logging

        // A project with ala Maven layout (src/main/javaPlugin, src/test/javaPlugin, ...)
        JkJavaProject coreProject = JkJavaProject.ofMavenLayout("../dev.jeka.core-samples")
             .getDependencyManagement()
                .addDependencies(JkDependencySet.of()
                        .and("junit:junit:4.13", JkJavaDepScopes.TEST)).__;

        // A project depending on the first project + Guava
        JkJavaProject dependerProject = JkJavaProject.ofMavenLayout(".")
            .getDependencyManagement()
                .addDependencies(JkDependencySet.of()
                .and("com.google.guava:guava:22.0")
                .and(coreProject)).__;
        dependerProject.getMaker().getSteps()
                .getPublishing()
                    .setVersionedModule("mygroup:depender", "1.0-SNAPSHOT");

        coreProject.getMaker().clean();
        dependerProject.getMaker().clean().makeAllArtifacts();
        dependerProject.getMaker().getSteps().getPublishing().publish();
    }
}
