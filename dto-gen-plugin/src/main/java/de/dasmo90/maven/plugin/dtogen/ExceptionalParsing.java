package de.dasmo90.maven.plugin.dtogen;

import java.lang.reflect.Type;
import java.util.Map;

public interface ExceptionalParsing {

	boolean canParse(Type type);

	void parse(StringBuilder sb, DtoAttribute attr, Map<String, String> oldNameToNewName);
}
