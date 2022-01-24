package dev.jeka.core.tool;

import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsFile;
import dev.jeka.core.api.utils.JkUtilsObject;
import dev.jeka.core.api.utils.JkUtilsString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class Environment {

    private Environment() {
        // Can't be instantiated
    }

    static CommandLine commandLine = CommandLine.parse(new String[0]);

    static StandardOptions standardOptions = new StandardOptions(Collections.emptyMap());

    static void initialize(String[] commandLineArgs) {


        List<String> effectiveCommandLineArgs = new LinkedList<>(Arrays.asList(commandLineArgs));

        // Add arguments contained in cmd.properties '_append'
        Map<String, String> presets = projectCmdProperties();
        List<String> appendedArgs = Arrays.asList(JkUtilsString.translateCommandline(presets.get("_append")));
        effectiveCommandLineArgs.addAll(appendedArgs);

        // Interpolate arguments passed as $key to respective value
        for (ListIterator<String> it = effectiveCommandLineArgs.listIterator(); it.hasNext(); ) {
            String word = it.next();
            if (word.startsWith("$")) {
                String presetValue = presets.get(word.substring(1));
                if (presetValue != null) {
                    String[] replacingItems = JkUtilsString.translateCommandline(presetValue);
                    it.remove();
                    Arrays.stream(replacingItems).forEach(item -> it.add(item));
                }
            }
        }
        JkLog.trace("Effective command line : " + effectiveCommandLineArgs);

        // Parse command line
        final CommandLine commandLine = CommandLine.parse(effectiveCommandLineArgs.toArray(new String[0]));

        final StandardOptions standardOptions = new StandardOptions(commandLine.getStandardOptions());
        if (standardOptions.logVerbose) {
            JkLog.setVerbosity(JkLog.Verbosity.VERBOSE);
        }
        if (standardOptions.logIvyVerbose) {
            JkLog.setVerbosity(JkLog.Verbosity.QUITE_VERBOSE);
        }
        Environment.commandLine = commandLine;
        Environment.standardOptions = standardOptions;
    }

    /**
     * By convention, standard options start with upper case.
     */
    static class StandardOptions {

        boolean logIvyVerbose;

        boolean logVerbose;

        boolean logNoAnimation;

        boolean logBanner;

        boolean logSetup;

        boolean logStackTrace;

        JkLog.Style logStyle;

        String logRuntimeInformation;

        boolean ignoreCompileFail;

        private String jkBeanName;

        private boolean workClean;

        private final Set<String> names = new HashSet<>();

        StandardOptions (Map<String, String> map) {
            this.logVerbose = valueOf(Boolean.class, map, false, "Log.verbose", "lv");
            this.logIvyVerbose = valueOf(Boolean.class, map, false, "log.ivy.verbose", "liv");
            this.logNoAnimation = valueOf(Boolean.class, map, false, "log.no.animation", "lna");
            this.logBanner = valueOf(Boolean.class, map, false,"log.banner", "lb");
            this.logSetup = valueOf(Boolean.class, map, false,"log.setup", "lsu");
            this.logStackTrace = valueOf(Boolean.class, map,false, "log.stacktrace", "lst");
            this.logRuntimeInformation = valueOf(String.class, map, null, "log.runtime.info", "lri");
            this.logStyle = valueOf(JkLog.Style.class, map, JkLog.Style.INDENT, "log.style", "ls");
            this.jkBeanName = valueOf(String.class, map, null, "kbean", "kb");
            this.ignoreCompileFail = valueOf(Boolean.class, map, false, "def.compile.ignore-failure", "dci");
            this.workClean = valueOf(Boolean.class, map, false, "work.clean", "wc");
        }

        Set<String> names() {
            return names;
        }

        String jkCBeanName() {
            return jkBeanName;
        }

        boolean workClean() {
            return workClean;
        }

        @Override
        public String toString() {
            return "JkBean" + JkUtilsObject.toString(jkBeanName) + ", LogVerbose=" + logVerbose
                    + ", LogHeaders=" + logBanner;
        }

        private <T> T valueOf(Class<T> type, Map<String, String> map, T defaultValue, String ... optionNames) {
            for (String name : optionNames) {
                this.names.add(name);
                if (map.containsKey(name)) {
                    String stringValue = map.get(name);
                    try {
                        return (T) FieldInjector.parse(type, stringValue);
                    } catch (IllegalArgumentException e) {
                        throw new JkException("Option " + name + " has been set with improper value '" + stringValue + "'");
                    }
                }
            }
            return defaultValue;
        }
    }

    private static Map<String, String> projectCmdProperties() {
        Path presetCommandsFile = Paths.get("jeka/cmd.properties");
        if (Files.exists(presetCommandsFile)) {
            return JkUtilsFile.readPropertyFileAsMap(presetCommandsFile);
        }
        return Collections.emptyMap();
    }

}
