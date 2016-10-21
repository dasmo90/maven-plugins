package de.dasmo90.maven.plugin.dtogen;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.reflections.Configuration;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

	private List<Class> interfaces;
	private List<DtoClass> generated;

	public DtoGenMojo() {
		LOG = new MojoLogger(this.getLog());
	}

	private void load() throws MalformedURLException, DependencyResolutionRequiredException {

		List<URL> urls = new LinkedList<>();
		for (String elt : project.getCompileSourceRoots()) {
			URL url = new File(elt).toURI().toURL();
			urls.add(url);
			LOG.debug("Source root: " + url);
		}
		for (String elt : project.getCompileClasspathElements()) {
			URL url = new File(elt).toURI().toURL();
			urls.add(url);
			LOG.debug("Compile classpath: " + url);
		}

		Configuration configuration = new ConfigurationBuilder()
				.filterInputsBy(
						new FilterBuilder().includePackage(packagePrefixes.toArray(new String[packagePrefixes.size()])))
				.setUrls(urls)
				.setScanners(new SubTypesScanner(false), new ResourcesScanner());
		Reflections reflections = new Reflections(configuration);
		URLClassLoader child = new URLClassLoader(urls.toArray(new URL[urls.size()]),
				this.getClass().getClassLoader());

		// lambdas not allowed
		Set<String> allTypes;
		try {
			allTypes = reflections.getAllTypes();
		} catch (ReflectionsException e) {
			LOG.warn("No classes found in classpath.");
			interfaces = Collections.emptyList();
			return;
		}

		interfaces = allTypes.stream().map(new Function<String, Class>() {
			@Override
			public Class apply(String s) {
				try {
					return child.loadClass(s);
				} catch (ClassNotFoundException e) {
					return null;
				}
			}
		}).filter(new Predicate<Class>() {
			@Override
			public boolean test(Class c) {
				return c != null && c.isInterface();
			}
		}).collect(Collectors.toList());
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
			load();
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