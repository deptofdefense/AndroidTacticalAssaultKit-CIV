#include "shaders.h"
#include <iostream>
#include <vector>

#include <GLES2/gl2.h>

#include <util/Logging2.h>

std::string FragmentShaderCode =
"#version 300 es\n\
precision mediump float;\n\
in vec3 normal;\n\
in vec3 position;\n\
in vec2 texcoord;\n\
in float lum_adj;\n\
\n\
uniform sampler2D tex;\n\
uniform vec3 sun_position; \n\
uniform vec3 sun_color; \n\
\n\
out vec4 color;\n\
void main() {\n\
	float lum = max(dot(normal, normalize(sun_position)), 0.0) + lum_adj;\n\
	color = texture(tex, texcoord) * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n\
}\n\
";

std::string VertexShaderCode =
"#version 300 es\n\
layout(location = 0) in vec3 in_vertex;\n\
layout(location = 1) in vec3 in_normal;\n\
layout(location = 2) in vec2 in_texcoord;\n\
\n\
uniform mat4 MVP;\n\
\n\
out vec3 normal;\n\
out vec3 position;\n\
out vec2 texcoord;\n\
out float lum_adj;\n\
\n\
void main(){\n\
	gl_Position = MVP * vec4(in_vertex, 1);\n\
	position = gl_Position.xyz;\n\
    normal = mat3(MVP) * in_normal;\n\
    float normal_len = length(normal);\n\
    lum_adj = (step(-0.000001, normal_len)*(1.0-step(0.000001, normal_len)));\n\
	normal = normal / (normal_len + lum_adj);\n\
	position = in_vertex;\n\
	texcoord = in_texcoord;\n\
}";

using namespace TAK::Engine::Util;

//
Shaders::Shaders()
{

	// Create the shaders
	GLuint VertexShaderID = glCreateShader(GL_VERTEX_SHADER);
	GLuint FragmentShaderID = glCreateShader(GL_FRAGMENT_SHADER);


	GLint Result = GL_FALSE;
	int InfoLogLength;

	// Compile Vertex Shader
	char const * VertexSourcePointer = VertexShaderCode.c_str();
	glShaderSource(VertexShaderID, 1, &VertexSourcePointer, NULL);
	glCompileShader(VertexShaderID);

	// Check Vertex Shader
	glGetShaderiv(VertexShaderID, GL_COMPILE_STATUS, &Result);
	glGetShaderiv(VertexShaderID, GL_INFO_LOG_LENGTH, &InfoLogLength);
	if (InfoLogLength > 0) {
		std::vector<char> VertexShaderErrorMessage(InfoLogLength + 1);
		glGetShaderInfoLog(VertexShaderID, InfoLogLength, NULL, &VertexShaderErrorMessage[0]);
		printf("%s\n", &VertexShaderErrorMessage[0]);
		Logger_log(TELL_Error, "Failed to load vertexShader %s", &VertexShaderErrorMessage[0]);
	}

	// Compile Fragment Shader
	char const * FragmentSourcePointer = FragmentShaderCode.c_str();
	glShaderSource(FragmentShaderID, 1, &FragmentSourcePointer, NULL);
	glCompileShader(FragmentShaderID);

	// Check Fragment Shader
	glGetShaderiv(FragmentShaderID, GL_COMPILE_STATUS, &Result);
	glGetShaderiv(FragmentShaderID, GL_INFO_LOG_LENGTH, &InfoLogLength);
	if (InfoLogLength > 0) {
		std::vector<char> FragmentShaderErrorMessage(InfoLogLength + 1);
		glGetShaderInfoLog(FragmentShaderID, InfoLogLength, NULL, &FragmentShaderErrorMessage[0]);
		printf("%s\n", &FragmentShaderErrorMessage[0]);
	    Logger_log(TELL_Error, "Failed to load fragment Shader %s", &FragmentShaderErrorMessage[0]);
    }

	// Link the program
	printf("Linking program\n");
	GLuint ProgramID = glCreateProgram();
	glAttachShader(ProgramID, VertexShaderID);
	glAttachShader(ProgramID, FragmentShaderID);
	glLinkProgram(ProgramID);

	// Check the program
	glGetProgramiv(ProgramID, GL_LINK_STATUS, &Result);
	glGetProgramiv(ProgramID, GL_INFO_LOG_LENGTH, &InfoLogLength);
	if (InfoLogLength > 0) {
		std::vector<char> ProgramErrorMessage(InfoLogLength + 1);
		glGetProgramInfoLog(ProgramID, InfoLogLength, NULL, &ProgramErrorMessage[0]);
		printf("%s\n", &ProgramErrorMessage[0]);
        Logger_log(TELL_Error, "Failed to link program Shader %s", &ProgramErrorMessage[0]);
    }

	glDetachShader(ProgramID, VertexShaderID);
	glDetachShader(ProgramID, FragmentShaderID);

	glDeleteShader(VertexShaderID);
	glDeleteShader(FragmentShaderID);

	this->pid = ProgramID;
}


Shaders::~Shaders()
{
}
