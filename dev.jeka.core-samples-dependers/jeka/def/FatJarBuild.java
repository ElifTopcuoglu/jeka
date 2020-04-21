import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.samples.JavaPluginBuild;
import dev.jeka.core.tool.JkCommandSet;
import dev.jeka.core.tool.JkDefImport;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

/**
 * Simple build demonstrating of how Jerkar can handle multi-project build.
 * 
 * @author Jerome Angibaud
 * 
 * @formatter:off
 */
public class FatJarBuild extends JkCommandSet {

    JkPluginJava javaPlugin = getPlugin(JkPluginJava.class);
    
    @JkDefImport("../dev.jeka.core-samples")
    private JavaPluginBuild sampleBuild;

    @Override
    protected void setup() {
        javaPlugin.getProject()
            .getArtifactProducer()
                .putMainArtifact(javaPlugin.getProject().getPackaging()::createFatJar).__
            .getDependencyManagement()
                .addDependencies(JkDependencySet.of()
                        .and(sampleBuild.java.getProject().toDependency()))
        ;
    }
   
}
