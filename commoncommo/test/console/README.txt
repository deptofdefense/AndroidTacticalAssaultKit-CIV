USAGE

Run commotest -h for full help listing.
For typical tak server-based use, see the stream: and sstream: commands.
For load testing, see the safreq command


DISTRIBUTION
This test application is for internal testing use only.   Do not distribute.


CAVEATS
This application is used for rapid fire testing of common commo.  It is
intended for developer use only.
While the use of the Commo API is intended to be bug free, the application
itself (particularly the command line parser) is not extremely error
tolerant and only very limited error checking is done.  If you are seeing
application crashing or other strange behavior, especially before the
command set completes parsing, triple-check your commands against the
help as bad arguments are not always caught.  This will be improved
as time permits.


