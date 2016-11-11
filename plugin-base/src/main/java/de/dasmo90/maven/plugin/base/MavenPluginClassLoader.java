package de.dasmo90.maven.plugin.base;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.reflections.Configuration;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MavenPluginClassLoader extends ClassLoader {

	private static final Logger LOG = LoggerFactory.getLogger(MavenPluginClassLoader.class);

	private final MavenProject project;
	private final LinkedList<URL> urls;
	private final URLClassLoader urlClassLoader;

	public MavenPluginClassLoader(MavenProject project) throws DependencyResolutionRequiredException {
		this(project, project.getClassRealm());
	}

	public MavenPluginClassLoader(MavenProject project, ClassLoader classLoader) throws
			DependencyResolutionRequiredException {
		this.project = project;
		this.urls = new LinkedList<>();
		load();
		this.urlClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]),
				classLoader
		);
	}

	private void load() throws DependencyResolutionRequiredException {
		for (String elt : project.getCompileSourceRoots()) {
			addUrl(elt);
			LOG.debug("Added compile source roots element: {}", elt);
		}
		for (String elt : project.getCompileClasspathElements()) {
			addUrl(elt);
			LOG.debug("Added compile classpath element: {}", elt);
		}
	}

	private void addUrl(String jar) {
		try {
			final URL url = new File(jar).toURI().toURL();
			urls.add(url);
		} catch (MalformedURLException e) {
			LOG.warn("JAR \"{}\" has malformed URL.", jar);
		}
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return this.urlClassLoader.loadClass(name);
	}

	public List<Class<?>> loadClasses(Predicate<Class<?>> predicate, String... prefixes) {
		Configuration configuration = new ConfigurationBuilder()
				.filterInputsBy(new FilterBuilder().includePackage(prefixes))
				.setUrls(urls)
				.setScanners(new SubTypesScanner(false), new ResourcesScanner());
		Reflections reflections = new Reflections(configuration);

		Set<String> allTypes;
		try {
			allTypes = reflections.getAllTypes();
		} catch (ReflectionsException e) {
			LOG.warn("No classes found in classpath.");
			return Collections.emptyList();
		}
		List<Class<?>> collect = allTypes.stream().map(s -> {
			try {
				return loadClass(s);
			} catch (ClassNotFoundException e) {
				return null;
			}
		}).filter(Objects::nonNull).filter(predicate).collect(Collectors.toList());
		LOG.info("{} classes found in classpath.", collect.size());

		if (LOG.isDebugEnabled()) {
			LOG.debug("Found:\n{}",
					String.join("\n", collect.stream().map(Class::getName).sorted().collect(Collectors.toList())));
		}
		return collect;
	}

	public List<Class<?>> loadClasses(String... prefixes) {
		return loadClasses(c -> true, prefixes);
	}

	@Override
	public URL getResource(String name) {
		return this.urlClassLoader.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return this.urlClassLoader.getResources(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return this.urlClassLoader.getResourceAsStream(name);
	}

	@Override
	public void setDefaultAssertionStatus(boolean enabled) {
		this.urlClassLoader.setDefaultAssertionStatus(enabled);
	}

	@Override
	public void setPackageAssertionStatus(String packageName, boolean enabled) {
		this.urlClassLoader.setPackageAssertionStatus(packageName, enabled);
	}

	@Override
	public void setClassAssertionStatus(String className, boolean enabled) {
		this.urlClassLoader.setClassAssertionStatus(className, enabled);
	}

	@Override
	public void clearAssertionStatus() {
		this.urlClassLoader.clearAssertionStatus();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MavenPluginClassLoader that = (MavenPluginClassLoader) o;

		if (!project.equals(that.project)) return false;
		if (!urls.equals(that.urls)) return false;
		return urlClassLoader.equals(that.urlClassLoader);

	}

	@Override
	public int hashCode() {
		int result = project.hashCode();
		result = 31 * result + urls.hashCode();
		result = 31 * result + urlClassLoader.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "MavenPluginClassLoader{" +
				"project=" + project +
				", urls=" + urls +
				", urlClassLoader=" + urlClassLoader +
				'}';
	}
}
