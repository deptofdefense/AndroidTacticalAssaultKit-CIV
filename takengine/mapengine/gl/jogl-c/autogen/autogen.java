import java.io.*;
import java.util.*;
import java.util.regex.*;

public final class autogen {
	private final static Pattern FUNCTION_PROTOTYPE_REGEX = Pattern.compile("\\s*GL_APICALL\\s+([a-zA-Z\\s*]+)\\s*GL_APIENTRY\\s+([a-zA-Z0-9]+)\\s*\\((.*)\\)\\s*;\\s*");
	private final static Pattern FUNCTION_POINTER_REGEX = Pattern.compile("\\s*typedef\\s+[a-zA-Z\\s*]+\\s*\\(GL_APIENTRYP\\s*([A-Z0-9]+)\\)\\s*\\(.*\\)\\s*;\\s*");
	
	private final static class GLFunction {
		public String signature;
		public String returnType;
		public String[] args;
	}
	
	private static Properties loadProperties() throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream("autogen.properties");
			Properties props = new Properties();
			props.load(in);
			return props;
		} finally {
			if(in != null)
				in.close();
		}
	}

	private static String getParameterName(String paramDefinition) {
		String[] parts = paramDefinition.split("[\\s\\*]");
		return parts[parts.length-1];
	}

	private static String[] parseArgs(String argPart) {
		if(argPart == null || argPart.isEmpty())
			return new String[0];
		
		String[] args = argPart.split("\\,");
		if(args == null || args.length == 0)
			return new String[0];
		
		if(args.length == 1 && args[0].trim().equals("void"))
			return new String[0];
		
		for(int i = 0; i < args.length; i++)
			args[i] = getParameterName(args[i]);
		return args;
	}
	
	private static void appendArgs(GLFunction fn, StringBuilder glimpl) {
		if(fn.args == null || fn.args.length < 1)
			return;
		glimpl.append(fn.args[0]);
		for(int i = 1; i < fn.args.length; i++) {
			glimpl.append(',');
			glimpl.append(fn.args[i]);
		}
	}

	private static GLFunction parsePrototype(String prototype) {
		Matcher m = FUNCTION_PROTOTYPE_REGEX.matcher(prototype);
		if(!m.matches())
			throw new IllegalStateException();
		GLFunction fn = new GLFunction();
		fn.signature = prototype;
		fn.returnType = m.group(1).trim();
		fn.args = parseArgs(m.group(3));
		return fn;
	}

	private static String readFile(File f) throws IOException {
		if(f.length() > 0xFFFFFFFFL)
			throw new IllegalArgumentException();
		byte[] content = new byte[(int)f.length()];
		InputStream in = null;
		try {
			in = new FileInputStream(f);
			in.read(content);
			return new String(content);
		} finally {
			if(in != null)
				in.close();
		}
	}
	
	private static void writeFile(String f, String content) throws IOException {
		FileWriter out = null;
		try {
			out = new FileWriter(createFile(f));
			out.write(content);
		} finally {
			if(out != null)
				out.close();
		}
	}
	
	private static File createFile(String path) {
		File f = new File(path);
		f.getParentFile().mkdirs();
		return f;
	}

    public static void main(String[] args) throws Throwable {
		Properties props = loadProperties();
	
		final String header = props.getProperty("input.gles-header", null);
		if(header == null) {
			System.err.println("No GL header defined");
			return;
		}
		
		final String headerNoExt = header.replace(".h", "");
		final String headerAsApi = headerNoExt.toLowerCase().replaceAll("[\\\\\\/\\.]", "_");
		
		File headerFile = new File("../../khronos/OpenGL/api", header);
		if(!headerFile.exists()) {
			System.err.println("Unable to find input header: " + headerFile);
			return;
		}
		
		final String initFunction = props.getProperty("output.functiontable-init-function", null);
		if(initFunction == null) {
			System.err.println("No Function Table initialization function declared");
			return;
		}
		Collection<String> ignores = Arrays.asList(props.getProperty("output.ignore-functions", "").split("[\\,\\s]"));

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(headerFile));

			//  read in header parse out prototypes and pointers
			Map<String, GLFunction> prototypes = new LinkedHashMap<>();
			LinkedHashSet<String> pointers = new LinkedHashSet<>();
			do {
				String line = reader.readLine();
				if(line == null)
					break;
				Matcher m;
				m = FUNCTION_PROTOTYPE_REGEX.matcher(line);
				if(m.matches()) {
					prototypes.put(m.group(2), parsePrototype(line));
					continue;
				}
				m = FUNCTION_POINTER_REGEX.matcher(line);
				if(m.matches()) {
					pointers.add(m.group(1));
					continue;
				}
			} while(true);

			//  read function table header template
			String functionTableHeaderTemplate = readFile(new File("GLFunctionTable.h.template"));
			//  build function table content
			StringBuilder functionTableContent = new StringBuilder();
			for(Map.Entry<String, GLFunction> prototype : prototypes.entrySet()) {
				// make sure there is a function pointer defined
				if(!pointers.contains("PFN" + prototype.getKey().toUpperCase() + "PROC")) {
					System.out.println("skipping " + prototype.getKey() + " no pointer found");
					continue;
				}
				functionTableContent.append("    PFN");
				functionTableContent.append(prototype.getKey().toUpperCase());
				functionTableContent.append("PROC ");
				functionTableContent.append(prototype.getKey());
				functionTableContent.append(";\n");
			}
			
			
			// update header
			functionTableHeaderTemplate = functionTableHeaderTemplate.replace("%%glheader%%", header);
			// inject the API string for uniqueness
			functionTableHeaderTemplate = functionTableHeaderTemplate.replace("%%glapi%%", headerAsApi);
			
			// inject table content and write function table header
			writeFile("../src/GLFunctionTable.h", functionTableHeaderTemplate.replace("/**%%GL_FUNCTION_TABLE_CONTENT%%**/", functionTableContent.toString()));
			
			//  read function table implementation template
			String functionTableImplTemplate = readFile(new File("GLFunctionTable.c.template"));

            // XXX - fill in guts
			StringBuilder stubs = new StringBuilder();
			for(Map.Entry<String, GLFunction> prototype : prototypes.entrySet()) {
				// make sure there is a function pointer defined
				if(!pointers.contains("PFN" + prototype.getKey().toUpperCase() + "PROC")) {
					continue;
				}
				
				String stubDecl = prototype.getValue().signature.replace(prototype.getKey(), "stub_" + prototype.getKey()).replace("GL_APICALL", "").replace(";", "");
				stubs.append("static ");
				stubs.append(stubDecl);
				stubs.append('{');
				if(!prototype.getValue().returnType.equals("void")) {
					stubs.append("return (");
					stubs.append(prototype.getValue().returnType);
					stubs.append(")0;");
				}
				stubs.append("}\n");
			}
			
			StringBuilder stubInit = new StringBuilder();
			for(Map.Entry<String, GLFunction> prototype : prototypes.entrySet()) {
				// make sure there is a function pointer defined
				if(!pointers.contains("PFN" + prototype.getKey().toUpperCase() + "PROC")) {
					continue;
				}
				
				stubInit.append("    .");
				stubInit.append(prototype.getKey());
				stubInit.append('=');
				stubInit.append("stub_");
				stubInit.append(prototype.getKey());
				stubInit.append(",\n");
			}
			
			StringBuilder functionTableSet = new StringBuilder();
			for(Map.Entry<String, GLFunction> prototype : prototypes.entrySet()) {
				// make sure there is a function pointer defined
				final String fnptr = "PFN" + prototype.getKey().toUpperCase() + "PROC";
				if(!pointers.contains(fnptr)) {
					continue;
				}
				
				functionTableSet.append("    SET_FN_PTR(");
				functionTableSet.append(prototype.getKey());
				functionTableSet.append(',');
				functionTableSet.append(fnptr);
				functionTableSet.append(");\n");
			}
			
			// inject the API string for uniqueness
			functionTableImplTemplate = functionTableImplTemplate.replace("%%glapi%%", headerAsApi);
			// inject the stubs
			functionTableImplTemplate = functionTableImplTemplate.replace("/**%%FUNCTION-TABLE-STUBS%%**/", stubs.toString());
			// inject the function stub initialization
			functionTableImplTemplate = functionTableImplTemplate.replace("/**%%FUNCTION-TABLE-STUB-INIT%%**/", stubInit.toString());
			// inject the function pointer assignment
			functionTableImplTemplate = functionTableImplTemplate.replace("/**%%FUNCTION-TABLE-SET%%**/", functionTableSet.toString());
			
			// inject table content and write function table header
			writeFile("../src/GLFunctionTable.c", functionTableImplTemplate);
			
			// generate GL impl content
			StringBuilder glimpl = new StringBuilder();
			glimpl.append("#include <");
			glimpl.append(header);
			glimpl.append(">\n");
			glimpl.append("#include \"GLFunctionTable.h\"\n");
			
			for(Map.Entry<String, GLFunction> prototype : prototypes.entrySet()) {
				// make sure there is a function pointer defined
				if(!pointers.contains("PFN" + prototype.getKey().toUpperCase() + "PROC")) {
					continue;
				}
				glimpl.append(prototype.getValue().signature.replace(";", "{"));
				glimpl.append('\n');
				if(initFunction.equals(prototype.getKey())) {
					glimpl.append("    static int fntbl_init = 0;\n");
					glimpl.append("    if(!fntbl_init) {\n");
					glimpl.append("        ");
					glimpl.append(headerAsApi);
					glimpl.append("_init_fns();\n");
					glimpl.append("        fntbl_init = 1;\n");
					glimpl.append("    }\n");
				}

				glimpl.append("    ");
				if(!prototype.getValue().returnType.equals("void"))
					glimpl.append("return ");
				glimpl.append(headerAsApi);
				glimpl.append(".");
				glimpl.append(prototype.getKey());
				glimpl.append('(');
				// populate argument list
				appendArgs(prototype.getValue(), glimpl);
				glimpl.append(");\n");
				glimpl.append("}\n");
			}

			// generate GL impl C file
			writeFile("../src/" + header.replace(".h", ".c"), glimpl.toString());
		} finally {
			if(reader != null)
				reader.close();
		}
	}
}