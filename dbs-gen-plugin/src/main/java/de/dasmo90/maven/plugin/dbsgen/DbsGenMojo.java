package de.dasmo90.maven.plugin.dbsgen;

import de.dasmo90.maven.plugin.base.MavenPluginClassLoader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mojo(
		name = "generate-db-schema",
		defaultPhase = LifecyclePhase.GENERATE_SOURCES,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class DbsGenMojo extends AbstractMojo {

	private Logger LOG = LoggerFactory.getLogger(DbsGenMojo.class);

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(readonly = true, required = true)
	private List<String> packagePrefixes;


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