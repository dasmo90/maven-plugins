package de.dasmo90.maven.plugin.dtogen;

import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.plexus.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DtoClassGenerator {

	public static final String GET_METHOD_REGEX = "get([A-Z])(.*)";
	public static final String CLASS_NAME_REGEX = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
	public static final int NOT_PARSEABLE = -2;
	public static final int PARSEABLE = -1;
	private static final String SEMI_COLON_NEW_LINE = ";\n";
	private static final String SPACE_OPEN_CURLY_BRACE_NEW_LINE = " {\n";
	private static final String CLOSE_CURLY_BRACE_NEW_LINE = "}\n";
	private static final String NEW_LINE = "\n";
	private static final String SPACE = " ";
	private static final String OPEN_PARENTHESIS = "(";
	private static final String CLOSE_PARENTHESIS = ")";
	private final MojoLogger LOG;
	private final List<Class> interfaces;
	private final String suffix;
	private final boolean generateSetters;
	private final ExceptionalParsing[] exceptionalParsings;
	private final List<Class> collectedInterfaces = new LinkedList<>();
	private final Map<String, String> oldNameToNewName = new HashMap<>();
	private List<DtoClass> generated;
	private List<DtoAttribute> attrs;

	public DtoClassGenerator(MojoLogger log, String suffix, List<Class<?>> interfaces, boolean generateSetters) throws Exception {
		this.LOG = log;
		this.suffix = suffix;
		this.interfaces = new LinkedList<>(interfaces);
		this.generateSetters = generateSetters;
		exceptionalParsings = new ExceptionalParsing[]{new ListExceptionalParsing()};
	}

	private int checkType(Type type) {
		Matcher matcher = Pattern.compile(CLASS_NAME_REGEX).matcher(type.getTypeName());
		List<String> classNames = new ArrayList<>();
		while (matcher.find()) {
			classNames.add(matcher.group());
		}
		if (classNames.size() <= 1 || CollectionUtils.intersection(classNames, oldNameToNewName.keySet()).isEmpty()) {
			return PARSEABLE;
		}
		for (int i = 0; i < this.exceptionalParsings.length; i++) {
			if (exceptionalParsings[i].canParse(type)) {
				return i;
			}
		}
		return NOT_PARSEABLE;
	}

	private String getNewTypeName(Type type) {
		String typeName = type.getTypeName();
		Pattern pattern = Pattern.compile(CLASS_NAME_REGEX);
		Matcher matcher = pattern.matcher(typeName);
		while (matcher.find()) {
			String foundClass = matcher.group();
			for (Map.Entry<String, String> entry : this.oldNameToNewName.entrySet()) {
				if (entry.getKey().equals(foundClass)) {
					String suffix = typeName.substring(matcher.end());
					String prefix = typeName.substring(0, matcher.start());
					typeName = prefix + entry.getValue() + suffix;
					matcher = pattern.matcher(typeName);
				}
			}
		}
		return typeName;
	}

	private void collect(Class c) {
		oldNameToNewName.put(c.getName(), c.getName() + suffix);
		collectedInterfaces.add(c);
	}

	private void preCheck(Class c) throws UnsupportedInterfaceException {
		if (!c.isInterface()) {
			throw new UnsupportedInterfaceException("Can only handle interfaces: " + c.toString());
		}
		if (!ArrayUtils.isEmpty(c.getTypeParameters())) {
			throw new UnsupportedInterfaceException("Cannot handle typed interfaces: " + c.toString());
		}
		for (Method method : c.getMethods()) {
			if (!ArrayUtils.isEmpty(method.getTypeParameters())) {
				throw new UnsupportedInterfaceException(
						"Cannot handle interfaces with typed methods: " + method.toString());
			}
			if (!Pattern.compile(GET_METHOD_REGEX).matcher(method.getName()).matches()) {
				throw new UnsupportedInterfaceException(
						"Can only handle getter-interfaces, method not allowed: " + method.toString());
			}
			if (!ArrayUtils.isEmpty(method.getGenericParameterTypes())) {
				throw new UnsupportedInterfaceException(
						"Can only handle getter-interfaces, method not allowed: " + method.toString());
			}
			if (checkType(method.getGenericReturnType()) == NOT_PARSEABLE) {
				throw new UnsupportedInterfaceException(
						"Can only handle none generic types, method not allowed: " + method.toString());
			}
		}
	}

	public List<DtoClass> generate() {
		if (generated != null) {
			return generated;
		}

		for (Class i : interfaces) {

			try {
				preCheck(i);
				collect(i);

			} catch (UnsupportedInterfaceException e) {
				LOG.warn(e.getMessage());
			}

		}
		// lambdas not allowed
		generated = collectedInterfaces.stream().map(new Function<Class, DtoClass>() {
			@Override
			public DtoClass apply(Class c) {
				return DtoClassGenerator.this.generateDto(c);
			}
		}).collect(Collectors.toList());
		return generated;
	}

	private void preScan(Class c) {

		Pattern pattern = Pattern.compile(GET_METHOD_REGEX);
		attrs = new LinkedList<>();
		for (Method method : c.getMethods()) {
			Matcher matcher = pattern.matcher(method.getName());
			if (!ArrayUtils.isEmpty(method.getExceptionTypes())) {
				LOG.warn("Getter \"" + method.getName() + "\" throwing exception(s) which is not getter conform.");
			}
			if (matcher.matches()) {
				DtoAttribute attr = new DtoAttribute();
				attr.setName(matcher.group(1).toLowerCase() + matcher.group(2));
				attr.setMethodName(matcher.group(1) + matcher.group(2));
				attr.setReturnType(method.getGenericReturnType());
				attrs.add(attr);
			} else {
				throw new IllegalStateException("Pre check failed.");
			}
		}
	}

	private DtoClass generateDto(Class c) {

		DtoClass dtoClass = new DtoClass();
		dtoClass.setName(c.getName() + suffix);
		preScan(c);

		StringBuilder sb = new StringBuilder();
		sb.append("package ");
		sb.append(c.getPackage().getName());
		sb.append(SEMI_COLON_NEW_LINE);
		sb.append(NEW_LINE);
		sb.append("public class ");
		sb.append(c.getSimpleName());
		sb.append(suffix);
		sb.append(" implements ");
		sb.append(c.getSimpleName());
		sb.append(SPACE_OPEN_CURLY_BRACE_NEW_LINE);
		sb.append(NEW_LINE);

		for (DtoAttribute attr : this.attrs) {

			sb.append("\tprivate ");
			sb.append(getNewTypeName(attr.getReturnType()));
			sb.append(SPACE);
			sb.append(attr.getName());
			sb.append(SEMI_COLON_NEW_LINE);
			sb.append(NEW_LINE);

			if (generateSetters) {
				sb.append("\tpublic void set");
				sb.append(attr.getMethodName());
				sb.append(OPEN_PARENTHESIS);
				sb.append(getNewTypeName(attr.getReturnType()));
				sb.append(SPACE);
				sb.append(attr.getName());
				sb.append(CLOSE_PARENTHESIS);
				sb.append(SPACE_OPEN_CURLY_BRACE_NEW_LINE);
				sb.append("\t\tthis.");
				sb.append(attr.getName());
				sb.append(" = ");
				sb.append(attr.getName());
				sb.append(SEMI_COLON_NEW_LINE);
				sb.append("\t");
				sb.append(CLOSE_CURLY_BRACE_NEW_LINE);
				sb.append(NEW_LINE);
			}

			int i = checkType(attr.getReturnType());
			if (i == PARSEABLE) {
				sb.append("\tpublic ");
				sb.append(attr.getReturnType().getTypeName());
				sb.append(" get");
				sb.append(attr.getMethodName());
				sb.append(OPEN_PARENTHESIS);
				sb.append(CLOSE_PARENTHESIS);
				sb.append(SPACE_OPEN_CURLY_BRACE_NEW_LINE);
				sb.append("\t\treturn this.");
				sb.append(attr.getName());
				sb.append(SEMI_COLON_NEW_LINE);
				sb.append("\t");
				sb.append(CLOSE_CURLY_BRACE_NEW_LINE);
				sb.append(NEW_LINE);

			} else {
				this.exceptionalParsings[i].parse(sb, attr, this.oldNameToNewName);
			}
		}

		sb.append(NEW_LINE);
		sb.append(CLOSE_CURLY_BRACE_NEW_LINE);
		dtoClass.setContent(sb.toString());
		return dtoClass;
	}
}
