package de.dasmo90.maven.plugin.dbsgen;

import de.dasmo90.maven.plugin.base.MavenPluginClassLoader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Entity;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Mojo(
		name = "generate-db-schema",
		defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public final class DbsGenMojo extends AbstractMojo {

	private Logger LOG = LoggerFactory.getLogger(DbsGenMojo.class);

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(readonly = true, required = true)
	private List<String> packagePrefixes;

	@Parameter(readonly = true, required = true)
	private String dialect;

	@Parameter(readonly = true)
	private DbConnection connection;

	public void execute() throws MojoExecutionException {

		LOG.info("Scanning packages: {}", packagePrefixes);

		final List<Class<?>> entities;
		final MavenPluginClassLoader projectClassLoader;
		ClassLoader localClassLoader = this.getClass().getClassLoader();
		try {
			projectClassLoader = new MavenPluginClassLoader(project, localClassLoader);
			entities = projectClassLoader
					.loadClasses(c -> Objects.nonNull(c.getAnnotation(Entity.class)),
							packagePrefixes.toArray(new String[packagePrefixes.size()])
					);

		} catch (Exception e) {
			throw new MojoExecutionException("Failed to load interfaces from classpath.", e);
		}
		String dialectClass;
		try {
			switch (this.dialect) {
				case "mysql":
					dialectClass = MySQLDialect.class.getName();
					break;
				case "h2":
				case "h2db":
					dialectClass = H2Dialect.class.getName();
					break;
				case "hsql":
				case "hsqldb":
					dialectClass = HSQLDialect.class.getName();
					break;
				default:
					dialectClass = localClassLoader.loadClass(this.dialect).getName();
			}
		} catch (ClassNotFoundException e) {
			throw new MojoExecutionException("Dialect \"" + this.dialect + "\" is not available.");
		}
		try {
			Connection dbConnection = null;
			if(connection != null) {
				dbConnection = DriverManager.getConnection(
						connection.getUrl(),
						connection.getUser(),
						connection.getPassword());
			}
			StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
			registryBuilder.applySetting("hibernate.dialect", dialectClass);

			if(dbConnection != null) {
				LOG.info("Setting database connection to {}", connection.getUrl());
				registryBuilder.applySetting("javax.persistence.schema-generation-connection", dbConnection);
			}

			MetadataSources metadata = new MetadataSources(registryBuilder.build());

			for (final Class entity : entities) {
				metadata.addAnnotatedClass(entity);
				LOG.debug("Added entity: {}", entity.getName());
			}

			Thread.currentThread().setContextClassLoader(projectClassLoader);
			SchemaExport export = new SchemaExport();
			export.setDelimiter(";");

			if(connection != null) {
				export.create(EnumSet.of(TargetType.DATABASE), metadata.buildMetadata());
			} else {
				File target = this.project.getBasedir();
				File createSqlFile = new File(target, "target/create.sql");
				File dropSqlFile = new File(target, "target/drop.sql");
				File parent = createSqlFile.getParentFile();
				if (!parent.exists() && !parent.mkdirs()) {
					throw new IllegalStateException("Couldn't create dir: " + parent);
				}
				export.setFormat(true);
				export.setOutputFile(createSqlFile.getAbsolutePath());
				export.createOnly(EnumSet.of(TargetType.SCRIPT), metadata.buildMetadata());
				export.setOutputFile(dropSqlFile.getAbsolutePath());
				export.drop(EnumSet.of(TargetType.SCRIPT), metadata.buildMetadata());
			}

		} catch (Exception e) {
			throw new MojoExecutionException("Error during generation.", e);
		}
	}
}