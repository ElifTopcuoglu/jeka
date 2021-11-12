import dev.jeka.core.samples.JavaPluginBuild;
import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkDefImport;
import dev.jeka.core.tool.builtins.project.ProjectJkBean;

/**
 * Simple build demonstrating of how Jeka can handle multi-project build.
 * 
 * @author Jerome Angibaud
 * 
 * @formatter:off
 */
public class FatJarBuild extends JkClass {

    ProjectJkBean projectPlugin = getJkBean(ProjectJkBean.class);
    
    @JkDefImport("../dev.jeka.samples.basic")
    private JavaPluginBuild sampleBuild;

    @Override
    protected void setup() {
        projectPlugin.getProject()
            .getPublication()
                .getArtifactProducer()
                    .putMainArtifact(projectPlugin.getProject().getConstruction()::createFatJar)
                .__
            .__
            .simpleFacade()
                .setCompileDependencies(deps -> deps
                        .and("com.google.guava:guava:22.0")
                        .and(sampleBuild.projectPlugin.getProject().toDependency()));
    }
   
}
