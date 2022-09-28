package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.utils.JkUtilsAssert;
import dev.jeka.core.api.utils.JkUtilsString;

import java.nio.file.Path;
import java.util.*;

import static dev.jeka.core.api.utils.JkUtilsString.blankToNull;

/**
 * Set of identifiers for binary artifact.
 * Most of the time 1 coordinate identifies 1 artifacts, identified with its group name and version.
 *
 * Nevertheless, This is possible that a coordinate leads to several artifact, having same group/name/version
 * but with different classifier/type.
 *
 * @author Jerome Angibaud
 */
public final class JkCoordinate {

    private final JkModuleId moduleId;

    private final JkVersion version;

    private final Set<JkArtifactSpecification> artifactSpecifications;


    private JkCoordinate(JkModuleId moduleId, JkVersion version,
                         Set<JkArtifactSpecification> artifactSpecifications) {
        JkUtilsAssert.argument(moduleId != null, "module cannot be null.");
        JkUtilsAssert.argument(version != null, version + " version cannot be null.");
        JkUtilsAssert.argument(artifactSpecifications != null, moduleId + " artifactSpecifications cannot be null.");
        this.moduleId = moduleId;
        this.version = version;
        this.artifactSpecifications = artifactSpecifications;
    }

    /**
     * Creates a {@link JkCoordinate} to the specified getModuleId with unspecified version
     */
    @SuppressWarnings("unchecked")
    public static JkCoordinate of(JkModuleId jkModuleId) {
        return of(jkModuleId, JkVersion.UNSPECIFIED);
    }

    /**
     * Creates a {@link JkCoordinate} to the specified getModuleId and
     * <code>JkVersion</code>.
     */
    @SuppressWarnings("unchecked")
    public static JkCoordinate of(JkModuleId jkModuleId, JkVersion version) {
        return new JkCoordinate(jkModuleId, version, Collections.singleton(JkArtifactSpecification.MAIN));
    }

    /**
     * Creates a {@link JkCoordinate} of the specified moduleId and version range.
     */
    @SuppressWarnings("unchecked")
    public static JkCoordinate of(JkModuleId jkModuleId, String version) {
        return jkModuleId.toCoordinate(version);
    }

    /**
     * Creates a {@link JkCoordinate} to its group, name and version
     * range. The version range can be any string accepted by
     * {@link JkVersion#of(String)}.
     */
    public static JkCoordinate of(String group, String name, String version) {
        return of(JkModuleId.of(group, name), JkVersion.of(version));
    }

    /**
     * Description can be :
     * group:name
     * group:name:version
     * group:name:classifiers...:version
     * group:name:classifiers:type:version
     *
     * classifiers may be a single classifier or a list as <i>linux,mac</i>. The empty string
     * stands for the default classifier.
     *
     * Version can be a '?' if it is unspecified or a '+' to take the highest existing version.
     */
    public static JkCoordinate of(String description) {
        final String[] strings = description.split( ":");
        final String errorMessage = "Dependency specification '" + description + "' is not correct. Should be one of \n" +
                "  group:name \n" +
                "  group:name:version \n" +
                "  group:name:classifiers:version \n" +
                "  group:name:classifiers:extension:version \n" +
                "  group:name:classifiers:extension: \n" +
                "where classifiers can be a coma separated list of classifier.";
        JkUtilsAssert.argument(isCoordinateDescription(description), errorMessage);
        int separatorCount = JkUtilsString.countOccurrence(description, ':');
        final JkModuleId jkModuleId = JkModuleId.of(strings[0], strings[1]);
        if (separatorCount == 1 && strings.length == 2) {
            return of(jkModuleId, JkVersion.UNSPECIFIED);
        }
        if (separatorCount == 2 && strings.length == 3) {
            return of(jkModuleId, JkVersion.of(strings[2]));
        }
        if (separatorCount == 3 && strings.length == 4) {
            return of(jkModuleId, JkVersion.of(strings[3])).withClassifiers(blankToNull(strings[2]));
        }
        if (separatorCount == 4 && strings.length == 3) {
            return of(jkModuleId, JkVersion.UNSPECIFIED).withClassifiersAndType(blankToNull(strings[2]), null);
        }
        if (separatorCount == 4 && strings.length == 4) {
            return of(jkModuleId, JkVersion.UNSPECIFIED).withClassifiersAndType(blankToNull(strings[2]),
                    blankToNull(strings[3]));
        }
        if (separatorCount == 4 && strings.length == 5) {
            return of(jkModuleId, JkVersion.of(strings[4])).withClassifiersAndType(blankToNull(strings[2])
                    , blankToNull(strings[3]));
        }
        throw new IllegalArgumentException(errorMessage);
    }

    /**
     * Returns <code>true</code> if the specified candidate matches to a module description.
     * @see #of(String)
     */
    public static boolean isCoordinateDescription(String candidate) {
        final String[] strings = candidate.split( ":");
        return strings.length >= 2 && strings.length <= 5;
    }

    /**
     * Returns the getModuleId of this dependency.
     */
    public JkModuleId getModuleId() {
        return moduleId;
    }

    /**
     * Returns the version of the module this dependency is constrained to.
     */
    public JkVersion getVersion() {
        return version;
    }

    /**
     * Returns <code>true</code> if the version of the module for this dependency is not specified.
     */
    public boolean hasUnspecifiedVersion() {
        return this.version.isUnspecified();
    }

    /**
     * Returns a JkModuleDependency identical to this one but with the specified
     * static version. If the specified version is <code>null</code> then returned version is this one.
     *
     */
    public JkCoordinate withVersion(JkVersion version) {
        if (version == null) {
            return this;
        }
        return new JkCoordinate(moduleId, version, artifactSpecifications);
    }

    public JkCoordinate withVersion(String version) {
        if (version == null) {
            return this;
        }
        return withVersion(JkVersion.of(version));
    }

    /**
     * @see #withClassifiersAndType(String, String)
     */
    public JkCoordinate withClassifiers(String classifier) {
        return withClassifiersAndType(classifier, null);
    }

    /**
     * Returns a JkModuleDependency identical to this one but with the specified
     * classifiers and type as the only {@link JkArtifactSpecification} for this dependency
     * @param classifiers classifiers separated with ','. Example 'linux,max' stands for
     *                    linux and mac classifier. ',mac' stands for the default classifier +
     *                    mac classifier
     */
    public JkCoordinate withClassifiersAndType(String classifiers, String type) {
        Set<JkArtifactSpecification> artifactSpecifications = new LinkedHashSet<>();
        for (String classifier : (classifiers + " ").split(",")) {
            artifactSpecifications.add(new JkArtifactSpecification(classifier.trim(), type));
        }
        return new JkCoordinate(moduleId, version, Collections.unmodifiableSet(artifactSpecifications));
    }

    /**
     * @see #andClassifierAndType(String, String)
     */
    public JkCoordinate andClassifier(String classifier) {
        return andClassifierAndType(classifier, null);
    }

    /**
     * Returns a JkModuleDependency identical to this one but adding the specified
     * classifier and type {@link JkArtifactSpecification}.
     */
    public JkCoordinate andClassifierAndType(String classifier, String type) {
        JkArtifactSpecification artifactSpecification = new JkArtifactSpecification(classifier, type);
        Set<JkArtifactSpecification> set = new LinkedHashSet<>(this.artifactSpecifications);
        if (set.isEmpty()) {
            set.add(JkArtifactSpecification.MAIN);
        }
        set.add(artifactSpecification);
        return new JkCoordinate(moduleId, version, Collections.unmodifiableSet(set));
    }

    /**
     * Returns the {@link JkArtifactSpecification}s for this module dependency. It can e empty if no
     * artifact specification as been set. In this case, only the main artifact is taken in account.
     */
    public Set<JkArtifactSpecification> getArtifactSpecifications() {
        return this.artifactSpecifications;
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(moduleId.toString());
        if (!version.isUnspecified()) {
            result.append(":" + version);
        }
        artifactSpecifications.forEach(spec ->
                result.append("(classifier=" + spec.classifier + ", type=" + spec.type + ")"));
        return result.toString();
    }


    /**
     * When declaring a module dependency, we implicitly request for the main artifact of this module. Nevertheless,
     * we can request for getting others artifacts in place or additionally of the main one.
     * This class aims at specifying which artifact are concerned for the dependency.
     */
    public static class JkArtifactSpecification {

        /** Stands for the main artifact */
        public static final JkArtifactSpecification MAIN = new JkArtifactSpecification(null, null);

        private final String classifier;

        private final String type;

        private JkArtifactSpecification (String classifier, String type) {
            this.classifier = classifier;
            this.type = type;
        }

        public static JkArtifactSpecification of(String classifier, String type) {
            return new JkArtifactSpecification(classifier, type);
        }

        public String getClassifier() {
            return classifier;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            JkArtifactSpecification that = (JkArtifactSpecification) o;

            if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) return false;
            if (type != null ? !type.equals(that.type) : that.type != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = classifier != null ? classifier.hashCode() : 0;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JkCoordinate that = (JkCoordinate) o;
        if (!moduleId.equals(that.moduleId)) return false;
        if (!version.equals(that.version)) return false;
        return  artifactSpecifications.equals(that.artifactSpecifications);
    }

    @Override
    public int hashCode() {
        int result = moduleId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + artifactSpecifications.hashCode();
        return result;
    }

    public Path cachePath() {
        String moduleName = this.moduleId.getName();
        Set<JkCoordinate.JkArtifactSpecification> artifactSpecifications =
                this.getArtifactSpecifications();
        JkCoordinate.JkArtifactSpecification artSpec = !this.getArtifactSpecifications().isEmpty() ?
                this.getArtifactSpecifications().iterator().next()
                : JkCoordinate.JkArtifactSpecification.of("", "jar");
        String type = JkUtilsString.isBlank(artSpec.getType()) ? "jar" : artSpec.getType();
        String fileName = cacheFileName();
        Path path = JkLocator.getJekaRepositoryCache()
                .resolve(this.moduleId.getGroup())
                .resolve(moduleName)
                .resolve(type + "s")
                .resolve(fileName);
        return path;
    }

    public String cacheFileName() {
        String moduleName = this.moduleId.getName();
        Set<JkCoordinate.JkArtifactSpecification> artifactSpecifications =
                this.getArtifactSpecifications();
        JkCoordinate.JkArtifactSpecification artSpec = !this.getArtifactSpecifications().isEmpty() ?
                this.getArtifactSpecifications().iterator().next()
                : JkCoordinate.JkArtifactSpecification.of("", "jar");
        String type = JkUtilsString.isBlank(artSpec.getType()) ? "jar" : artSpec.getType();
        String classifierElement = JkUtilsString.isBlank(artSpec.getClassifier()) ? "" : "-" + artSpec.getClassifier();
        return moduleName + "-" + this.getVersion() + classifierElement + "." + type;
    }

    public JkCoordinate resolveConflict(JkVersion other, ConflictStrategy strategy) {
        if (version.isUnspecified()) {
            return withVersion(other);
        }
        if (other.isUnspecified()) {
            return this;
        }
        if (strategy == ConflictStrategy.FAIL && !version.equals(other)) {
            throw new IllegalStateException(this.moduleId + " has been declared with both version '" + version +
                    "' and '" + other + "'");
        }
        if (version.isSnapshot() && !other.isSnapshot()) {
            return withVersion(other);
        }
        if (!version.isSnapshot() && other.isSnapshot()) {
            return this;
        }
        if (strategy == ConflictStrategy.TAKE_FIRST) {
            return this;
        }
        return strategy == ConflictStrategy.TAKE_HIGHEST && version.isGreaterThan(other) ?
                this : this.withVersion(other);
    }

    public enum ConflictStrategy {
        TAKE_FIRST, TAKE_HIGHEST, TAKE_LOWEST, FAIL
    }

}