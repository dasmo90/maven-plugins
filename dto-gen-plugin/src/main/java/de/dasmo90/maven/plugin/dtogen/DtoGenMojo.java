package de.dasmo90.maven.plugin.dtogen;

import de.dasmo90.maven.plugin.base.MavenPluginClassLoader;
import de.dasmo90.maven.plugin.base.MojoLogger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Mojo(
		name = "generate-dtos",
		defaultPhase = LifecyclePhase.GENERATE_SOURCES,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class DtoGenMojo extends AbstractMojo {

	private static final String TARGET_GENERATED_SOURCES = "target/generated-sources/";

	private static final String SUFFIX_REGEX = "[A-Z][A-Za-z].*";

	private MojoLogger LOG;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(readonly = true, required = true)
	private List<String> packagePrefixes;

	@Parameter(defaultValue = "Dto", readonly = true)
	private String suffix;

	@Parameter(defaultValue = "false", readonly = true)
	private boolean generateSetters;

	private List<Class<?>> interfaces;
	private List<DtoClass> generated;

	public DtoGenMojo() {
		LOG = new MojoLogger(this.getLog());
	}

	private void generate() throws Exception {
		generated = new DtoClassGenerator(LOG, suffix, interfaces, generateSetters).generate();
	}

	public void execute() throws MojoExecutionException {
		LOG.info("Start ...");

		if (!Pattern.compile(SUFFIX_REGEX).matcher(suffix).matches()) {
			throw new MojoExecutionException("Suffix has to follow the pattern: " + SUFFIX_REGEX);
		}

		try {
			interfaces = new MavenPluginClassLoader(project)
					.loadClasses(Class::isInterface,
							packagePrefixes.toArray(new String[packagePrefixes.size()]));

			for(Class c : interfaces) {
				LOG.info(c.getName());
			}
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to load interfaces from classpath.", e);
		}

		try {
			generate();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to generate classes.", e);
		}

		try {
			write();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to write generated classes.", e);
		}

		this.project.addCompileSourceRoot(TARGET_GENERATED_SOURCES);

		LOG.info("Success!");
	}

	private void write() throws IOException {
		for (DtoClass dtoClass : this.generated) {
			File target = this.project.getBasedir();
			File targetFile = new File(target, TARGET_GENERATED_SOURCES
					+ dtoClass.getName().replaceAll(Pattern.quote("."), "/") + ".java");
			File parent = targetFile.getParentFile();
			if (!parent.exists() && !parent.mkdirs()) {
				throw new IllegalStateException("Couldn't create dir: " + parent);
			}
			try (FileWriter fileWriter = new FileWriter(targetFile)) {
				fileWriter.write(dtoClass.getContent());
			}
		}
	}


}