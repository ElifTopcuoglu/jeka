package org.jerkar.api.depmanagement;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers.Caller;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.util.url.URLHandlerRegistry;
import org.jerkar.api.depmanagement.JkDependencyNode.JkModuleNodeInfo;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.system.JkLocator;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsObject;
import org.jerkar.api.utils.JkUtilsThrowable;

/**
 * Jerkar users : This class is not part of the public API !!! Please, Use
 * {@link JkDependencyResolver} instead. Ivy wrapper providing high level methods. The
 * API is expressed using Jerkar classes only (mostly free of Ivy classes).
 *
 * @author Jerome Angibaud
 */
final class IvyResolver implements InternalDepResolver {

    private static final Random RANDOM = new Random();

    private static final String[] IVY_24_ALL_CONF = new String[] { "*(public)" };

    private final Ivy ivy;

    private IvyResolver(Ivy ivy) {
        super();
        this.ivy = ivy;
    }

    private static IvyResolver of(IvySettings ivySettings) {
        final Ivy ivy = ivy(ivySettings);
        return new IvyResolver(ivy);
    }

    static Ivy ivy(IvySettings ivySettings) {
        final Ivy ivy = new Ivy();
        ivy.getLoggerEngine().popLogger();
        ivy.getLoggerEngine().setDefaultLogger(new IvyMessageLogger());
        ivy.getLoggerEngine().setShowProgress(JkLog.verbosity() == JkLog.Verbosity.VERBOSE);
        ivy.getLoggerEngine().clearProblems();
        IvyContext.getContext().setIvy(ivy);
        ivy.setSettings(ivySettings);
        ivy.bind();
        URLHandlerRegistry.setDefault(new IvyFollowRedirectUrlHandler());
        return ivy;
    }

    /**
     * Creates an <code>IvySettings</code> to the specified repositories.
     */
    private static IvySettings ivySettingsOf(JkRepoSet resolveRepos) {
        final IvySettings ivySettings = new IvySettings();
        IvyTranslations.populateIvySettingsWithRepo(ivySettings, resolveRepos);
        ivySettings.setDefaultCache(JkLocator.jerkarRepositoryCache().toFile());
        return ivySettings;
    }

    /**
     * Creates an instance using specified repository for publishing and the
     * specified repositories for resolving.
     */
    public static IvyResolver of(JkRepoSet resolveRepos) {
        IvySettings ivySettings = ivySettingsOf(resolveRepos);
        return of(ivySettings);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JkResolveResult resolve(JkVersionedModule moduleArg, JkDependencySet deps,
            JkResolutionParameters parameters, JkScope ... resolvedScopes) {

        final JkVersionedModule module;
        if (moduleArg == null) {
            module = anonymousVersionedModule();
        } else {
            module = moduleArg;
        }

        if (parameters == null) {
            parameters = JkResolutionParameters.of();
        }
        final DefaultModuleDescriptor moduleDescriptor = IvyTranslations.toPublicationLessModule(module, deps,
                parameters.getDefaultMapping(), deps.getVersionProvider());

        final String[] confs = toConfs(deps.getDeclaredScopes(), resolvedScopes);
        final ResolveOptions resolveOptions = new ResolveOptions();
        resolveOptions.setConfs(confs);
        resolveOptions.setTransitive(true);
        resolveOptions.setOutputReport(JkLog.verbosity() == JkLog.Verbosity.VERBOSE);
        resolveOptions.setLog(logLevel());
        resolveOptions.setRefresh(parameters.isRefreshed());
        resolveOptions.setCheckIfChanged(true);
        if (resolvedScopes.length == 0) {   // if no scope, verbose ivy report turns in exception
            resolveOptions.setOutputReport(false);
        }
        final ResolveReport ivyReport;
        try {
            ivyReport = ivy.resolve(moduleDescriptor, resolveOptions);
        } catch (final Exception e) {
            throw JkUtilsThrowable.unchecked(e);
        }
        final JkResolveResult.JkErrorReport errorReport;
        if (ivyReport.hasError()) {
            errorReport = JkResolveResult.JkErrorReport.failure(moduleProblems(
                    ivyReport.getDependencies()));
        } else {
            errorReport = JkResolveResult.JkErrorReport.allFine();
        }
        final ArtifactDownloadReport[] artifactDownloadReports = ivyReport.getAllArtifactsReports();
        final IvyArtifactContainer artifactContainer = IvyArtifactContainer.of(artifactDownloadReports);
        final JkResolveResult resolveResult = getResolveConf(ivyReport.getDependencies(), module,
                errorReport, artifactContainer);
        if (moduleArg == null) {
            deleteResolveCache(module);
        }
        return resolveResult;
    }

    private void deleteResolveCache(JkVersionedModule module) {
        final ResolutionCacheManager cacheManager = this.ivy.getSettings().getResolutionCacheManager();
        final ModuleRevisionId moduleRevisionId = IvyTranslations.toModuleRevisionId(module);
        final File propsFile = cacheManager.getResolvedIvyPropertiesInCache(moduleRevisionId);
        propsFile.delete();
        final File xmlFile = cacheManager.getResolvedIvyFileInCache(moduleRevisionId);
        xmlFile.delete();
    }

    private static String logLevel() {
        if (JkLog.Verbosity.MUTE == JkLog.verbosity()) {
            return "quiet";
        }
        if (JkLog.Verbosity.VERBOSE == JkLog.verbosity()) {
            return "verbose";
        }
        return "download-only";
    }

    private static JkResolveResult getResolveConf(List<IvyNode> nodes,
            JkVersionedModule rootVersionedModule,
            JkResolveResult.JkErrorReport errorReport,
            IvyArtifactContainer ivyArtifactContainer) {

        // Compute dependency tree
        final JkDependencyNode tree = createTree(nodes, rootVersionedModule, ivyArtifactContainer);
        return JkResolveResult.of(tree, errorReport);
    }

    private static JkVersionedModule anonymousVersionedModule() {
        final String version = Long.toString(RANDOM.nextLong());
        return JkVersionedModule.of(JkModuleId.of("anonymousGroup", "anonymousName"), JkVersion.of(version));
    }

    @Override
    public File get(JkModuleDependency dependency) {
        final ModuleRevisionId moduleRevisionId = IvyTranslations.toModuleRevisionId(dependency.getModuleId(),
                dependency.getVersion());
        final boolean isMetadata = "pom".equalsIgnoreCase(dependency.getExt());
        final String typeAndExt = JkUtilsObject.firstNonNull(dependency.getExt(), "jar");
        final DefaultArtifact artifact;
        if (isMetadata) {
            artifact = new DefaultArtifact(moduleRevisionId, null, dependency.getModuleId().getName(), typeAndExt,
                    typeAndExt, true);
        } else {
            final Map<String, String> extra = new HashMap<>();
            if (dependency.getClassifier() != null) {
                extra.put("classifier", dependency.getClassifier());
            }
            artifact = new DefaultArtifact(moduleRevisionId, null, dependency.getModuleId().getName(), typeAndExt,
                    typeAndExt, extra);
        }
        final ArtifactDownloadReport report = ivy.getResolveEngine().download(artifact, new DownloadOptions());
        return report.getLocalFile();
    }

    private static JkDependencyNode createTree(Iterable<IvyNode> nodes, JkVersionedModule rootVersionedModule,
            IvyArtifactContainer artifactContainer) {
        final IvyTreeResolver treeResolver = new IvyTreeResolver(nodes, artifactContainer);
        final JkModuleNodeInfo treeRootNodeInfo = JkModuleNodeInfo.ofRoot(rootVersionedModule);
        return treeResolver.createNode(treeRootNodeInfo);
    }

    private static class IvyTreeResolver {

        // parent to children parentChildMap
        private final Map<JkModuleId, List<JkModuleNodeInfo>> parentChildMap = new HashMap<>();

        IvyTreeResolver(Iterable<IvyNode> nodes, IvyArtifactContainer artifactContainer) {


            for (final IvyNode node : nodes) {
                if (node.isCompletelyBlacklisted()) {
                    continue;
                }

                final Caller[] callers = node.getAllCallers();
                final JkModuleId moduleId = JkModuleId.of(node.getId().getOrganisation(), node.getId().getName());
                final JkVersion resolvedVersion = JkVersion.of(node.getResolvedId().getRevision());
                final Set<JkScope> rootScopes = IvyTranslations.toJkScopes(node.getRootModuleConfigurations());

                List<Path> artifacts;
                if (!node.isCompletelyEvicted()) {
                    artifacts = artifactContainer.getArtifacts(moduleId.getVersion(resolvedVersion.getValue()));
                } else {
                    artifacts = new LinkedList<>();
                }

                for (final Caller caller : callers) {
                    final DependencyDescriptor dependencyDescriptor = caller.getDependencyDescriptor();
                    final JkVersionedModule parent = IvyTranslations.toJkVersionedModule(caller.getModuleRevisionId());
                    final List<JkModuleNodeInfo> list = parentChildMap.computeIfAbsent(parent.getModuleId(), k -> new LinkedList<>());
                    final Set<JkScope> declaredScopes = IvyTranslations.toJkScopes(dependencyDescriptor.getModuleConfigurations());
                    final JkVersion version = JkVersion.of(dependencyDescriptor
                            .getDynamicConstraintDependencyRevisionId().getRevision());

                    final JkModuleNodeInfo moduleNodeInfo  = new JkModuleNodeInfo(moduleId, version, declaredScopes,
                            rootScopes, resolvedVersion, artifacts);
                    if (!containSame(list, moduleId)) {
                        list.add(moduleNodeInfo);
                    }
                }
            }
        }

        private static boolean containSame(List<JkModuleNodeInfo> list, JkModuleId moduleId) {
            for (final JkModuleNodeInfo moduleNodeInfo : list) {
                if (moduleNodeInfo.getModuleId().equals(moduleId)) {
                    return true;
                }
            }
            return false;
        }

        JkDependencyNode createNode(JkModuleNodeInfo holder) {
            if (parentChildMap.get(holder.getModuleId()) == null || holder.isEvicted()) {
                return JkDependencyNode.ofModuleDep(holder, new LinkedList<>());
            }

            List<JkModuleNodeInfo> moduleNodeInfos = parentChildMap.get(holder.getModuleId());
            if (moduleNodeInfos == null) {
                moduleNodeInfos = new LinkedList<>();
            }
            final List<JkDependencyNode> childNodes = new LinkedList<>();
            for (final JkModuleNodeInfo moduleNodeInfo : moduleNodeInfos) {
                final JkDependencyNode childNode = createNode(moduleNodeInfo);
                childNodes.add(childNode);
            }
            return JkDependencyNode.ofModuleDep(holder, childNodes);
        }

    }


    private List<JkModuleDepProblem> moduleProblems(List<IvyNode> ivyNodes) {
        final List<JkModuleDepProblem> result = new LinkedList<>();
        for (final IvyNode ivyNode : ivyNodes) {
            if (ivyNode.isCompletelyBlacklisted() || ivyNode.isCompletelyEvicted()) {
                continue;
            }
            if (ivyNode.hasProblem()) {
                final JkModuleId jkModuleId = JkModuleId.of(ivyNode.getModuleId().getOrganisation(), ivyNode.getModuleId().getName());
                final JkModuleDepProblem problem = JkModuleDepProblem.of(jkModuleId,
                        ivyNode.getId().getRevision(),
                        ivyNode.getProblemMessage());
                result.add(problem);
            }
        }
        return result;
    }

    private String[] toConfs(Set<JkScope> declaredScopes, JkScope ... resolvedScopes) {
        if (resolvedScopes.length == 0) {
            return IVY_24_ALL_CONF;
        }
        final Set<String> result = new HashSet<>();
        for (final JkScope resolvedScope : resolvedScopes) {
            final List<JkScope> scopes = resolvedScope.getCommonScopes(declaredScopes);
            for (final JkScope scope : scopes) {
                result.add(scope.getName());
            }
        }
        return JkUtilsIterable.arrayOf(result, String.class);
    }

}
