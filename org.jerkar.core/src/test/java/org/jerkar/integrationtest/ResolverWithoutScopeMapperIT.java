package org.jerkar.integrationtest;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.COMPILE;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.DEFAULT_SCOPE_MAPPING;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.RUNTIME;
import static org.jerkar.api.depmanagement.JkJavaDepScopes.TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jerkar.api.depmanagement.*;
import org.jerkar.api.utils.JkUtilsSystem;
import org.junit.Test;

public class ResolverWithoutScopeMapperIT {

    private static final JkRepoSet REPOS = JkRepo.ofMavenCentral().toSet();

    private static final JkScope MY_SCOPE = JkScope.of("myScope");

    @Test
    public void resolveCompile() {
        JkDependencySet deps = JkDependencySet.of()
                .and(JkPopularModules.APACHE_COMMONS_DBCP, "1.4", COMPILE);
        JkDependencyResolver resolver = JkDependencyResolver.of(REPOS)
                .withParams(JkResolutionParameters.of(DEFAULT_SCOPE_MAPPING));
        JkResolveResult resolveResult = resolver.resolve(deps, COMPILE);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertEquals(2, resolveResult.getDependencyTree().getResolvedVersion().getModuleIds().size());

        deps = JkDependencySet.of()
                .and(JkPopularModules.HIBERNATE_CORE, "5.2.10.Final", COMPILE);
        resolver = JkDependencyResolver.of(REPOS)
                .withParams(JkResolutionParameters.of(DEFAULT_SCOPE_MAPPING));
        resolveResult = resolver.resolve(deps, COMPILE);
        System.out.println(resolveResult.getDependencyTree().toStringComplete());
        assertEquals(10, resolveResult.getDependencyTree().getResolvedVersion().getModuleIds().size());
    }

    @Test
    public void resolveInheritedScopes() {
        JkDependencySet deps = JkDependencySet.of().and(JkPopularModules.APACHE_COMMONS_DBCP, "1.4", COMPILE);
        JkDependencyResolver resolver = JkDependencyResolver.of(REPOS)
            .withParams(JkResolutionParameters.of(DEFAULT_SCOPE_MAPPING));

        // runtime classpath should embed the dependency as well cause 'RUNTIME' scope extends 'COMPILE'
        JkResolveResult resolveResult = resolver.resolve(deps, RUNTIME);
        assertEquals(2, resolveResult.getDependencyTree().getResolvedVersion().getModuleIds().size());
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));

        // test classpath should embed the dependency as well
        resolveResult = resolver.resolve(deps, TEST);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertTrue(resolveResult.contains(JkModuleId.of("commons-dbcp")));
        assertEquals(2, resolveResult.getDependencyTree().getResolvedVersion().getModuleIds().size());
    }

    @Test
    public void resolveWithOptionals() {
        JkDependencySet deps = JkDependencySet.of()
                .and(JkPopularModules.SPRING_ORM, "4.3.8.RELEASE", JkScopeMapping.of(COMPILE).to("compile", "master", "optional"));
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepo.ofMavenCentral().toSet());
        JkResolveResult resolveResult = resolver.resolve(deps, COMPILE);
        assertEquals(38, resolveResult.getDependencyTree().getResolvedVersion().getModuleIds().size());
    }

    @Test
    public void resolveSpringbootTestStarter() {
        JkDependencySet deps = JkDependencySet.of()
                .and("org.springframework.boot:spring-boot-starter-test:1.5.3.RELEASE", JkScopeMapping.of(TEST).to("master", "runtime"));
        JkDependencyResolver resolver = JkDependencyResolver.of(JkRepo.ofMavenCentral().toSet());
        JkResolveResult resolveResult = resolver.resolve(deps, TEST);
        Set<JkModuleId> moduleIds = resolveResult.getDependencyTree().getResolvedVersion().getModuleIds();
        assertEquals("Wrong modules size " + moduleIds, 24, moduleIds.size());

    }

}
