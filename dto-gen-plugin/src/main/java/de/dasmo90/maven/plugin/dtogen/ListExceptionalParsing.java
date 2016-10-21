package de.dasmo90.maven.plugin.dtogen;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListExceptionalParsing implements ExceptionalParsing {

	public static final String TEMPLATE_PATH = "velocity/java.util.List.java.vm";

	private final Template template;

	public ListExceptionalParsing() throws Exception {
		/*  first, get and initialize an engine  */
		VelocityEngine ve = new VelocityEngine();
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());

		ve.init();
		/*  next, get the Template  */
		template = ve.getTemplate(TEMPLATE_PATH);
	}

	public boolean canParse(Type type) {
		Matcher matcher = Pattern.compile(DtoClassGenerator.CLASS_NAME_REGEX).matcher(type.getTypeName());
		return matcher.find() && matcher.group().equals(List.class.getName()) && matcher.find();
	}

	public void parse(StringBuilder sb, DtoAttribute attr, Map<String, String> oldNameToNewName) {
		String typeName = attr.getReturnType().getTypeName();
		Matcher matcher = Pattern.compile(DtoClassGenerator.CLASS_NAME_REGEX).matcher(typeName);

		if(!matcher.find() || !matcher.group().equals(List.class.getName()) || !matcher.find()) {
			throw new IllegalArgumentException("Cannot parse: " + typeName);
		}

		String interfac = matcher.group();

        /*  create a context and add data */
		VelocityContext context = new VelocityContext();
		context.put("methodName", attr.getMethodName());
		context.put("attrName", attr.getName());
		context.put("interface", interfac);
		String dto = oldNameToNewName.get(interfac);
		if (dto == null) {
			throw new IllegalArgumentException("Unnecessary: " + typeName);
		}
		context.put("dto", dto);
		/* now render the template into a StringWriter */
		StringWriter writer = new StringWriter();
		try {
			template.merge(context, writer);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
        sb.append(writer.toString());
	}
}
