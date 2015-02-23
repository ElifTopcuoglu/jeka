package org.jake;
import org.jake.depmanagement.JakeDependencies;
import org.jake.depmanagement.JakeScope;
import org.jake.publishing.JakeIvyPublication;
import org.jake.publishing.JakeMavenPublication;
import org.jake.publishing.JakePublishRepos;

/**
 * Build class for Jake itself.
 * This build relies on a dependency manager.
 * This build uses built-in extra feature as sonar, jacoco analysis.
 */
@JakeImport({
	"com.google:guava:16.0"
})
public class DepManagedBuild extends Build {

	public static final JakeScope DISTRIB = JakeScope.of("distrib").descr("Contains Jake distribution zip file");

	@Override
	protected JakeDependencies dependencies() {
		return JakeDependencies.builder()
				.usingDefaultScopes(PROVIDED)
				.on("junit:junit:4.11")
				.on("org.apache.ivy:ivy:2.4.0-rc1").build();
	}

	@Override
	public void base() {
		JakeOptions.forceVerbose(true);
		super.base();
		doc();
		publish();
	}

	public static void main(String[] args) {
		new DepManagedBuild().base();
	}

	@Override
	protected JakeMavenPublication mavenPublication() {
		return super.mavenPublication().and(distripZipFile, DISTRIB.name());
	}

	@Override
	protected JakeIvyPublication ivyPublication() {
		return super.ivyPublication().and(distripZipFile, "distrib", DISTRIB);
	}

	@Override
	protected JakePublishRepos publishRepositories() {
		return JakePublishRepos.maven(baseDir().file("build/output/dummyMavenRerpo"))
				.andIvy(JakePublishRepos.ACCEPT_ALL, (baseDir().file("build/output/dummyIvyRerpo")));
	}

	@Override
	protected boolean includeTestsInPublication() {
		return true;
	}

}
