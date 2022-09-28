package dev.jeka.core.integrationtest.resolution;

import dev.jeka.core.api.depmanagement.*;
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.resolution.JkResolveResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResolveHibernateIT {

    private static final JkRepoSet REPOS = JkRepo.ofMavenCentral().toSet();

    @Test
    public void resolveCompile() {
        JkDependencySet deps = JkDependencySet.of()
                .and(JkPopularLibs.APACHE_COMMONS_DBCP.toCoordinate("1.4"));
        JkDependencyResolver resolver = JkDependencyResolver.of().setRepos(REPOS);
        JkResolveResult resolveResult = resolver.resolve(deps);
        assertTrue(resolveResult.contains(JkModuleId.of("commons-pool")));
        assertEquals(2, resolveResult.getDependencyTree().getResolvedVersions().getModuleIds().size());

        deps = JkDependencySet.of()
                .and(JkPopularLibs.HIBERNATE_CORE.toCoordinate("5.2.10.Final"));
        resolver = JkDependencyResolver.of().setRepos(REPOS);
        resolveResult = resolver.resolve(deps);
        System.out.println(resolveResult.getDependencyTree().toStringTree());
        assertEquals(10, resolveResult.getDependencyTree().getResolvedVersions().getModuleIds().size());
    }

}
