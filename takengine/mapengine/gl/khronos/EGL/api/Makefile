# Copyright (c) 2013-2018 The Khronos Group Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Generated headers
EGLHEADERS = EGL/egl.h EGL/eglext.h

# Generation tools
PYTHON = python3
PYFILES = genheaders.py reg.py
REGISTRY = egl.xml
GENOPTS =
GENHEADERS = $(PYTHON) -B genheaders.py $(GENOPTS) -registry $(REGISTRY)

all: $(EGLHEADERS)

EGL/egl.h: egl.xml $(PYFILES)
	$(GENHEADERS) EGL/egl.h

EGL/eglext.h: egl.xml $(PYFILES)
	$(GENHEADERS) EGL/eglext.h

# Simple test to make sure generated headers compile
KHR   = .
TESTS = Tests

tests: egltest.c $(EGLHEADERS)
	$(CC) -c -I$(KHR) egltest.c
	$(CXX) -c -I$(KHR) egltest.c
	-rm egltest.o

# Verify registries against the schema

validate:
	jing -c registry.rnc egl.xml

################################################

# Remove intermediate targets from 'make tests'
clean:
	rm -f *.[io] Tests/*.[io] diag.txt dumpReg.txt errwarn.txt

clobber: clean
	rm -f $(EGLHEADERS)
