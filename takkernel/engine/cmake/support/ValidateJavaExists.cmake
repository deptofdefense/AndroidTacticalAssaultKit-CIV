# Validates that a Java installation (specifically a JDK) exists on the machine and matches the build architecture
# being targeted. The name of the CMake project validating the existence of Java should be passed in as the "project_name"
# argument for logging purposes, as multiple CMake projects may be requiring Java.
#
# Due to an existing bug in the FindJNI module, FindJNI will not respect the JAVA_HOME CMake environment variable if a
# JDK is specified on the system PATH. Thus, if there are multiple JDKs on the system, the first JDK found in PATH
# will be validated against. See https://gitlab.kitware.com/cmake/cmake/-/issues/19193 and
# https://gitlab.kitware.com/cmake/cmake/-/issues/13942.
function(validate_java_exists project_name)
    # Check if Java is defined on the system PATH by executing command "java -version".
    execute_process(COMMAND java -version
                    RESULT_VARIABLE JAVA_VERSION_EXIT_CODE
                    OUTPUT_QUIET
                    ERROR_QUIET)

    
    # If Java is found on the PATH, specify the exec command as `java`
    if(JAVA_VERSION_EXIT_CODE EQUAL 0)
        set(JAVA_EXEC_COMMAND "java")
        message(STATUS "Found JDK on PATH for project ${project_name}.")
    # If not, look for a JAVA_HOME system environment variable and construct the exec command from the path
    elseif(DEFINED ENV{JAVA_HOME})
        set(JAVA_EXEC_COMMAND "$ENV{JAVA_HOME}/bin/java")
        message(STATUS "Found JAVA_HOME environment variable pointed at $ENV{JAVA_HOME} for project ${project_name}.")
    # If neither of those exist, then the FindJNI module will be unable to find the JNI headers.
    else()
        message(FATAL_ERROR "Unable to find a JDK installation, please set JAVA_HOME or add the Java executable to your PATH.")
    endif()
	
	# Execute Java to determine if 64-bit architecture
	execute_process(COMMAND "${JAVA_EXEC_COMMAND}" -d64 -version
					RESULT_VARIABLE JAVA_ARCHITECTURE_EXIT_CODE
					OUTPUT_QUIET
					ERROR_QUIET)

	# Store the result of the architecture check as a BOOL.
	if(JAVA_ARCHITECTURE_EXIT_CODE EQUAL 0)
		set(JAVA_IS_64_BIT TRUE)
	endif()

	# If we're targeting 32-bit and the Java executable on the path is 32-bit, log that the correct executable was found.
	# Otherwise, if a 64-bit executable was found, throw an error.
	if(CMAKE_SYSTEM_PROCESSOR STREQUAL x86)
		if(NOT JAVA_IS_64_BIT)
			message(STATUS "Using 32-bit Java to generate build for project ${project_name}.")
		else()
			message(FATAL_ERROR "Discovered 64-bit Java specified while targeting a 32-bit build.")
		endif()
	# If we're targeting 64-bit and the Java executable on the path is 64-bit, log that the correct executable was found.
	# Otherwise, if a 32-bit executable was found, throw an error.
	else()
		if(JAVA_IS_64_BIT)
			message(STATUS "Using 64-bit Java to generate build for project ${project_name}.")
		else()
			message(FATAL_ERROR "32-bit Java specified while targeting a 64-bit build.")
		endif()
	endif()
endfunction()

