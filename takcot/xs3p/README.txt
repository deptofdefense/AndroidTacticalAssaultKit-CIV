xs3p - Version 1.0
==================

Index
-----
-Description
-Generating the documentation
-Viewing the documentation
-Configuring the stylesheet

Description
-----------

xs3p is a schema documentation generator. It is an XSLT stylesheet 
that will output an XHTML document from an XSD schema. Aside from 
using as much W3C acronyms as possible, the tool makes schemas more 
readable by providing a view of schema components' constraints as a 
sample XML instance, links that allow the user to jump to the 
documentation of schema components that are referenced, and a whole 
host of neat features. However, most important of all, it makes your  
schemas look 'pretty in pink'.

Generating the documentation
----------------------------

To generate the documentation, you need a command-line XSLT tool that
supports the W3C XSLT 1.0 recommendation. (NOTE: xs3p does not work
with <?xml-stylesheet ?> processing instruction because the <A> links
in the generated XHTML document will not be correct.)

Here are a few examples of XSLT tools:

1. MSXML 4.0 (Windows only)
---------------------------

You can download MSXML 4.0 SP1 at:

http://msdn.microsoft.com/downloads/default.asp?
url=/downloads/sample.asp?url=/msdn-
files/027/001/766/msdncompositedoc.xml

(If the URL is incorrect, go to http://msdn.microsoft.com, and search 
for the keywords, "msxml 4.0".)

Microsoft's web page gives you instructions of how to install the 
MSXML 4.0 library. You'll also need to download Msxsl which is a 
wrapper to access the library from the command-line. You can download 
it at:

http://msdn.microsoft.com/downloads/default.asp?
URL=/downloads/sample.asp?url=/MSDN-
FILES/027/001/485/msdncompositedoc.xml

(Again, if the URL is incorrect, go to http://msdn.microsoft.com, and
search for the keyword, "msxsl".)

Click on the "Download the executable file" link. Save the msxsl.exe 
file in a directory specified in the PATH environment variable. In 
most cases, you can dump the file in c:\winnt (or c:\windows 
depending on your OS) directory since it's already in the PATH 
variable.

Type: msxsl -? to learn how to use the tool. The basic usage is:

msxsl [source] [stylesheet] -o [result] {[param-name]=[param-value]...}

2. Xalan (Java-based)
---------------------

You can download Xalan from Apache at:

http://xml.apache.org/xalan-j/getstarted.html

The web page gives you instructions of how to install Xalan. You can 
find out how to use Xalan's command-line tool at:

http://xml.apache.org/xalan-j/commandline.html

The basic usage is:

java org.apache.xalan.xslt.Process -IN [source] -XSL [stylesheet]
-OUT [result] {-PARAM [param-name] [param-value]...}

3. Saxon (Java-based)
---------------------

You can download Saxon from Sourceforge at:

http://sourceforge.net/projects/saxon

If you download the saxon package, it comes with instructions of how 
to install and use the Java jar files.

If you download the instant saxon package, it comes with a .exe file, 
and all you have to do is put the file in a directory in the PATH 
environment variable. It also comes with instant.html file, which 
documents how to use it. The basic usage is:

saxon -o [result] [source] [stylesheet] {[param-name]=[param-value]...}

Viewing the documentation
-------------------------

To view the documentation, you need a browser that supports the W3C
XHTML 1.0 and CSS 2 recommendations, e.g.
-Internet Explorer 5.5 and above
-Netscape 6 and above
-Opera 5.0 and above
-Mozilla 1.0

Configuring the stylesheet
--------------------------

The xs3p stylesheet can be customised through its global parameters, 
which are listed below.

title :-
	You can specify your own title for the document, rather than 
using the default one.

sortByComponent :-
	If this parameter is set to "true", the schema components are 
presented sorted by type and name. Otherwise, they are presented in 
the order that they appear in the schema. By default, this parameter 
is set to "true."

useJavaScript :-
	The generated XHTML document uses JavaScript to hide some 
details like the underlying schema component XML representation, 
which can be made to appear with a button press. Since some people 
have ideological objections to JavaScript, this feature can be turned 
off. If this parameter is set to "true", JavaScript will be used in 
the generated documentation. Otherwise, it won't. By default, this 
parameter is set to "true."

printAllSuperTypes :-
	The type hierarchy of a global type definition is displayed in 
its section. If this parameter is set to "true", all super-types of 
the current type are shown in the type hierarchy. Otherwise, only the 
immediate parent type is displayed. By default, this parameter is set 
to "true."

printAllSubTypes :-
	This parameter has a similar concept as  printAllSuperTypes. If 
it is set to "true", all sub-types of the current type are shown in 
the type hierarchy. Otherwise, only the direct sub-types are 
displayed. By default, this parameter is set to "true."

printLegend :-
	If this parameter is set to "true", the Legend section is 
included. Otherwise, it isn't. By default, this parameter is set to 
"true."

printGlossary :-
	If this parameter is set to "true", the Glossary section is 
included. Otherwise, it isn't. By default, this parameter is set to 
"true."

printNSPrefixes :-
	If this parameter is set to "true", namespace information is 
provided when displaying sample instances and references. This is 
done by providing a prefix in front of tags and references, which 
when clicked, will take the user to the declared namespace. The 
prefix matches the prefix in the namespace declaration in the schema. 
If not set to "true", namespace prefixes are not displayed. By 
default, this parameter is set to "true."

searchIncludedSchemas :-
	If this parameter is set to "true", xs3p will search for 
components in "included" schemas when creating links and generating 
the XML Instance Representation table. 
	When this parameter is set to "true", the "linksFile" parameter 
must also be set, which is described below. Otherwise, an error will 
be raised. 
	The XSLT processor will also raise an error if it can't find the 
schemas specified in the "schemaLocation" attribute of "include" 
elements. XSLT implementations seem to resolve relative addresses 
relative to the XSLT script, rather than the source file. This is a
common cause of error since in schemas, the "schemaLocation" 
attribute is specified relative to the current schema. It is probably 
best to write a script (e.g. Ant build file, DOS batch file, or Unix 
shell script), which copies the xs3p stylesheet to the directory 
containing the set of schemas and execute the XSLT script based on 
the local copy.
	The final thing to note is that this search is recursive, so 
schemas "included" in the current schema's "included" schemas will 
also be searched. This is another common source of error, since 
schemas may be searched that might not have been thought about. 
	There are some batch files in the examples/ directory to show
how to use this feature. If you have 'msxsl', run "test_msxsl.bat"
file. If you use 'xalanj', run "test_xalanj.bat" file. Otherwise,
edit the files for your specific XSLT processor.

searchImportedSchemas :-
	If this parameter is set to "true", xs3p will search for 
components in "imported" schemas when creating links and generating 
the XML Instance Representation table. The above discussion for the 
"searchIncludedSchemas" parameter also applies to this parameter. 
Also, when this parameter is set to "true", the "linksFile" parameter 
must also be set.

linksFile :-
	Specifies the file which maps from file locations of 
"included" and "imported" schemas to the file locations of their 
xs3p-generated documentation. This file must be provided if either 
"searchIncludedSchemas" or "searchImportedSchemas" is set to true. 
	If relative addresses are used to specify the location of 
external xs3p-generated documentation, they must be relative to 
documentation file currently generated. NOTE: The external 
documentation files does not need to exist at the time of generating 
the documentation for the current schema.
	The mapping is specified in XML. The dtd and schema for this 
mapping syntax are "links.dtd" and "links.xsd" respectively. 
	A sample links file, "links.xml", is provided in the examples/
directory.
	NOTE: The "xmlns" namespace attribute with the correct 
namespace must be provided in the mapping file for the xs3p 
stylesheet to work.
	


COPYRIGHT AND LICENCE

Copyright (C) 2001, 2002 DSTC Pty Ltd.