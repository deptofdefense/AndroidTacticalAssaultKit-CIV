call "C:\Program Files (x86)\Microsoft Visual Studio 14.0\VC\vcvarsall.bat" x86 8.1 >NUL 2>&1
%MSB_ARGS%
exit %errorlevel%
