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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MavenPluginClassLoader {

	private static final Logger LOG = LoggerFactory.getLogger(MavenPluginClassLoader.class);

	private final MavenProject project;
	private final LinkedList<URL> urls;
	private final URLClassLoader urlClassLoader;

	public MavenPluginClassLoader(MavenProject project) throws DependencyResolutionRequiredException {
		this.project = project;
		this.urls = new LinkedList<>();
		load();
		this.urlClassLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]),
				project.getClassRealm() /*this.getClass().getClassLoader()*/);
	}

	private void load() throws DependencyResolutionRequiredException {
		for (String elt : project.getCompileSourceRoots()) {
			addUrl(elt);
		}
		for (String elt : project.getCompileClasspathElements()) {
			addUrl(elt);
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

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return this.urlClassLoader.loadClass(name);
	}

	public List<Class<?>> loadClasses(Predicate<Class<?>> predicate, String... prefixes) {
		Configuration configuration = new ConfigurationBuilder()
				.filterInputsBy(new FilterBuilder().includePackage(prefixes))
				.setUrls(urls)
				.setScanners(new SubTypesScanner(false), new ResourcesScanner());
		Reflections reflections = new Reflections(configuration);

		// lambdas not allowed
		Set<String> allTypes;
		try {
			allTypes = reflections.getAllTypes();
		} catch (ReflectionsException e) {
			LOG.warn("No classes found in classpath.");
			return Collections.emptyList();
		}
		return allTypes.stream().map(new Function<String, Class<?>>() {
			@Override
			public Class<?> apply(String s) {
				try {
					return loadClass(s);
				} catch (ClassNotFoundException e) {
					return null;
				}
			}
		}).filter(new Predicate<Class<?>>() {
			@Override
			public boolean test(Class<?> c) {
				return c != null;
			}
		}).filter(predicate).collect(Collectors.toList());
	}

	public List<Class<?>> loadClasses(String... prefixes) {
		return loadClasses(new Predicate<Class<?>>() {
			@Override
			public boolean test(Class<?> aClass) {
				return true;
			}
		}, prefixes);
	}
}
