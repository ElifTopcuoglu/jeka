package org.jerkar.tool;

import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependencyResolver;

/**
 * A plugin base class to extend to alter {@link JkBuild} object.
 *
 * @author Jerome Angibaud
 */
public abstract class JkBuildPlugin {

    public Class<? extends JkBuild> baseBuildClass() {
	return JkBuild.class;
    }

    public abstract void configure(JkBuild build);

    /**
     * Override this method if you want your plugin do something while
     * {@link JkBuild#verify} is invoked.
     */
    protected void verify() {
	// Do nothing by default
    }

    /**
     * Override this method if the plugin need to alter the dependency resolver.
     *
     * @see JkBuildDependencySupport#dependencyResolver()
     */
    protected JkDependencyResolver alterDependencyResolver(JkDependencyResolver original) {
	return original;
    }

    /**
     * Override this method if the plugin need to alter the dependencies.
     *
     * @see JkBuildDependencySupport#dependencies
     */
    protected JkDependencies alterDependencies(JkDependencies original) {
	return original;
    }

    public static void applyVerify(Iterable<? extends JkBuildPlugin> plugins) {
	for (final JkBuildPlugin plugin : plugins) {
	    plugin.verify();
	}
    }

    public static JkDependencyResolver applyDependencyResolver(Iterable<? extends JkBuildPlugin> plugins,
	    JkDependencyResolver original) {
	JkDependencyResolver result = original;
	for (final JkBuildPlugin plugin : plugins) {
	    result = plugin.alterDependencyResolver(original);
	}
	return result;
    }

    public static JkDependencies applyDependencies(Iterable<? extends JkBuildPlugin> plugins, JkDependencies original) {
	JkDependencies result = original;
	for (final JkBuildPlugin plugin : plugins) {
	    result = plugin.alterDependencies(original);
	}
	return result;
    }



    @Override
    public String toString() {
	return this.getClass().getSimpleName() + " : " + JkOptions.fieldOptionsToString(this);
    }

}
