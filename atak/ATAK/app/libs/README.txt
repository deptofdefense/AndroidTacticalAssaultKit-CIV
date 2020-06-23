ATAK makes use of libraries protected by thirdparty licenses.  


At the time of this licensing document, libraries that make use of the standard GPL licensing are 
not allowed.   Libraries that make use of a dual or tri licensing scheme can be used as long the 
usage of the libraries complies with a permitted licensing scheme within ATAK.


Any jar files put in version control should have the appropriate license also checked in and the 
terms of the license should be followed.  Please update the below list of software and refer to 
the appropriately checked in license.


Additionally, please be aware that xyz library might have the best things since sliced bread, but 
if we only use it to do one small function we are negatively impacting the DEX method count.

        for FILE in  *.jar;do 
             $ANDROID_SDK/build-tools/20.0.0/dx --dex --output=/tmp/temp.dex $FILE
             echo "$FILE"
             cat /tmp/temp.dex | head -c 92 | tail -c 4 | hexdump -e '1/4 "%d\n"'
        done


Please make sure to keep the license terms up to date in: 

                   ATAK/assets/license


LICENSE INFORMATION
=========================================================================

Note:

commons-lang-2.6-rockwell
-------------------------

distributed as source code.   the jar file is created manually by

      ( cd src && jar xvf ../libs/commons-lang-2.6-rockwell.jar )
	  find src -name *.class -exec rm {} \; 
	  rm -fr src/META-INF
	  rm libs/commons-lang-2.6-rockwell.jar         
			   make changes to the project source or add / remove files

	  ant release
	  cp -R src/org/apache/commons/lang bin/classes/org/apache/commons
      ( cd bin/classes && jar cvf ../../libs/commons-lang-2.6-rockwell.jar org/apache/commons )
	  ( cd src && rm -fr org/apache/commons/lang )


datadroid-2.1.2
---------------

distributed as an android project.   It has been compiled using the android build system and the 
resulting classes were placed in the jar file.   

    wget https://github.com/foxykeep/DataDroid/archive/master.zip
	unzip master.zip && cd DataDroid-master/DataDroid
	android update project -p `pwd` -t android-15
	ant release
	cd bin/classes
	jar cvf datadroid-2.1.2.jar .
DataDroid was modified to return an error message (e.g. HTTP Reason Phrase) in addition
to an error code (e.g. HTTP Status Code) SVN revision on 21 June 2016
	
	
simple-kml
---------------

distributed as an android project.   It has been compiled using the android build system and the 
resulting classes were placed in the jar file. Be sure to use Java 1.6 compiler. Note the current
jar was built pulling the .zip link below on 11 Nov 2013, plus the following manually applied
bug fixes as noted by "BYOUNG" in the jar.
https://github.com/Ekito/Simple-KML/issues/6
https://github.com/Ekito/Simple-KML/issues/8
https://github.com/Ekito/Simple-KML/issues/9
https://atakmap.com/bugz/show_bug.cgi?id=1825
https://atakmap.com/bugz/show_bug.cgi?id=2907
https://atakmap.com/bugz/show_bug.cgi?id=2916
https://atakmap.com/bugz/show_bug.cgi?id=5213

	wget https://github.com/Ekito/Simple-KML/archive/master.zip
	unzip master.zip && cd Simple-KML-master/SimpleKML
	android update project -p . -t android-15
	ant release
	cd bin/classes
	jar cvf simple-kml.jar .
	
	
opennmea-0.1
---------------

distributed as source.   It has been compiled using the android build system and the 
resulting classes were placed in the jar file. Be sure to use Java 1.6 compiler.

	wget http://downloads.sourceforge.net/project/opennmea/opennmea/v0.1/OpenNMEA-v0.1.zip
	unzip OpenNMEA-v0.1.zip
	android update project -p . -t android-15
	ant release
	cd bin/classes
	jar cvf opennmea-0.1.jar .


asmack
---------------
asmack is from:
http://gtalksms.googlecode.com/hg/libs/asmack-android-7.jar

which is a build of this fork of asmack:
https://github.com/Flowdalic/asmack

plus a patch to BOSH code which fixes a crash:
http://kenai.com/jira/browse/JBOSH-24?page=com.atlassian.jira.plugin.system.issuetabpanels%3Aall-tabpanel
