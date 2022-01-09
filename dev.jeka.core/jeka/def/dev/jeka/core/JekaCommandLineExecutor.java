package dev.jeka.core;

import dev.jeka.core.api.java.JkJavaProcess;
import dev.jeka.core.api.system.JkProcess;
import dev.jeka.core.api.utils.JkUtilsPath;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.api.utils.JkUtilsSystem;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for running jeka executables.
 */
public abstract class JekaCommandLineExecutor {

    private final Path samplesRootDir;

    private final Path jekaDir;

    protected JekaCommandLineExecutor(Path samplesRootDir, Path jekaDistrib) {
        super();
        this.samplesRootDir = samplesRootDir;
        this.jekaDir = jekaDistrib;
    }

    protected JekaCommandLineExecutor(Path projectRootDir) {
        this(projectRootDir.resolve("samples"), projectRootDir.resolve("dev.jeka.core/jeka/output/distrib"));
    }

    protected JekaCommandLineExecutor(String projectRootDir) {
        this(Paths.get(projectRootDir));
    }

    protected void runJekaw(String projectDir, String cmdLine) {
        runJeka(true, projectDir, cmdLine);
    }

    protected void runJeka(String projectDir, String cmdLine) {
        runJeka(false, projectDir, cmdLine);
    }

    protected void runJeka(boolean useWrapper, String projectDir, String cmdLine) {
        Path dir = this.samplesRootDir.resolve(projectDir);
        String command = useWrapper ? cmdLine + " -Djeka.distrib.location=" + jekaDir.toAbsolutePath().normalize() : cmdLine;
        process(dir, useWrapper)
                .addParams(JkUtilsString.translateCommandline(command))
                .inheritJkLogOptions()
                .addParams("-dcf", "-lst")
                .setEnv("JEKA_JDK", JkJavaProcess.CURRENT_JAVA_HOME.normalize().toString())
                .run();
    }

    protected Path getJekaDir() {
        return jekaDir.toAbsolutePath().normalize();
    }

    private static String jekawCmd(Path dir) {
        String scriptName = JkUtilsSystem.IS_WINDOWS ? "jekaw.bat" : "jekaw";
        return JkUtilsPath.relativizeFromWorkingDir(dir.resolve(scriptName)).toAbsolutePath().normalize().toString();
    }

    private String jekaCmd() {
        String scriptName = JkUtilsSystem.IS_WINDOWS ? "jeka.bat" : "jeka";
        return JkUtilsPath.relativizeFromWorkingDir(this.jekaDir).resolve(scriptName).toAbsolutePath().normalize().toString();
    }

    private JkProcess process(Path workingDir, boolean useWrapper) {
        String cmd = useWrapper ? jekawCmd(workingDir) : jekaCmd();
        return JkProcess.of(cmd)
                .setWorkingDir(workingDir)
                .setLogCommand(true)
                .setLogOutput(true)
                .setFailOnError(true);
    }


}
