package de.dasmo90.maven.plugin.dbsgen;

import de.dasmo90.maven.plugin.base.MavenPluginClassLoader;
import de.dasmo90.maven.plugin.base.MojoLogger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;

@Mojo(
		name = "generate-db-schema",
		defaultPhase = LifecyclePhase.GENERATE_SOURCES,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class DbsGenMojo extends AbstractMojo {

	private MojoLogger LOG;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(readonly = true, required = true)
	private List<String> packagePrefixes;


	public DbsGenMojo() {
		LOG = new MojoLogger(this.getLog());
	}

	public void execute() throws MojoExecutionException {
		LOG.info("Start ...");

		try {
			new MavenPluginClassLoader(project)
					.loadClasses(packagePrefixes.toArray(new String[packagePrefixes.size()]));

		} catch (Exception e) {
			throw new MojoExecutionException("Failed to load interfaces from classpath.", e);
		}

		LOG.info("Success!");
	}
}