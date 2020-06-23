call "C:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\vcvarsall.bat" x86 >NUL 2>&1
%MSB_ARGS%
exit %errorlevel%
