package dev.jeka.core.api.depmanagement;

import dev.jeka.core.api.system.JkException;
import dev.jeka.core.api.system.JkLog;

import java.util.*;

/**
 * A structure to manage consistently dependencies and their resolution.
 * It contains a cache, a default scope for dependencies declared without scope.
 * @param <T> Parent type for chaining
 */
public class JkDependencyManagement<T> {

    private final Map<Set<JkScope>, JkResolveResult> dependencyCache = new HashMap<>();

    private final JkDependencyResolver<JkDependencyManagement> resolver;

    private JkScope[] defaultScope = JkJavaDepScopes.COMPILE_AND_RUNTIME;

    private boolean failOnDependencyResolutionError = true;

    /**
     * For parent chaining
     */
    public final T __;

    private JkDependencySet dependencies = JkDependencySet.of();

    private JkDependencyManagement(T __) {
        this.__ = __;
        resolver = JkDependencyResolver.of(this);
        resolver.addRepos(JkRepo.ofLocal(), JkRepo.ofMavenCentral());
    }

    public static <T> JkDependencyManagement<T> of(T parent) {
       return new JkDependencyManagement(parent);
    }

    public JkDependencySet getDependencies() {
        return this.dependencies;
    }

    public JkDependencyManagement<T> removeDependencies() {
        dependencyCache.clear();
        this.dependencies = JkDependencySet.of();
        return this;
    }

    public JkDependencyManagement<T> addDependencies(JkDependencySet dependencies) {
        dependencyCache.clear();;
        this.dependencies = this.dependencies.and(dependencies);
        return this;
    }

    public JkDependencyResolver<JkDependencyManagement> getResolver() {
        return resolver;
    }

    /**
     * If <code>true</code> this object will throw a JkException whenever a dependency resolution occurs. Otherwise
     * just log a warn message. <code>false</code> by default.
     */
    public JkDependencyManagement<T> setFailOnDependencyResolutionError(boolean fail) {
        this.failOnDependencyResolutionError = fail;
        return this;
    }

    public JkScope[] getDefaultScope() {
        return defaultScope;
    }

    public void setDefaultScope(JkScope[] defaultScope) {
        this.defaultScope = defaultScope;
    }

    // ------------

    public JkDependencyManagement<T> cleanCache() {
        dependencyCache.clear();
        return this;
    }

    /**
     * Returns dependencies declared for this project. Dependencies declared without specifying
     * scope are defaulted to scope {@link JkJavaDepScopes#COMPILE_AND_RUNTIME}
     */
    public JkDependencySet getScopeDefaultedDependencies() {
        return dependencies.withDefaultScopes(defaultScope);
    }

    /**
     * Returns lib paths standing for the resolution of this project dependencies for the specified dependency scopes.
     */
    public JkResolveResult fetchDependencies(JkScope... scopes) {
        final Set<JkScope> scopeSet = new HashSet<>(Arrays.asList(scopes));
        return dependencyCache.computeIfAbsent(scopeSet,
                scopes1 -> {
                    JkResolveResult resolveResult =
                            resolver.resolve(getScopeDefaultedDependencies(), scopes);
                    JkResolveResult.JkErrorReport report = resolveResult.getErrorReport();
                    if (report.hasErrors()) {
                        if (failOnDependencyResolutionError) {
                            throw new JkException(report.toString());
                        }
                        JkLog.warn(report.toString());
                    }
                    return resolveResult;
                });
    }

}
