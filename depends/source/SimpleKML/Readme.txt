Note the current jar was built pulling the .zip link below on 11 Nov 2013, plus the following manually applied
bug fixes as noted by "BYOUNG" in the source.

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