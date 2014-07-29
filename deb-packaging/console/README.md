Apache Syncope Console WebApp - Debian Package
==============================================

Licensed under Apache License 2.0 - http://www.apache.org/licenses/LICENSE-2.0

Generates a Debian package for installing Apache Syncope WebConsole with Apache Tomcat 7 servlet container from Debian.
Source available at [https://github.com/ieugen/apache-syncope-debian](https://github.com/ieugen/apache-syncope-debian).

### Requirements

The project can be built on the following system:

* Debian Wheezy or later
* OpenJDK-7
* Maven 3
* tomcat 7

#### Console module

  Repackages official Apache Syncope Console war to include some customisations - mainly the logging configuration.
It also includes **configuration.properties** which provides the link to Syncope Core webapp.
Include here all your other console custom configs that can't be changed after build.

#### Building

To build the project install openjdk7, maven and git to grab the sources.

~~~
    git clone git@github.com:ieugen/apache-syncope-debian.git
    cd console
    mvn clean install
    sudo dpkg -i target/apache-syncope-console-$version.deb
~~~

Syncope Web Console is available at [http://localhost:8080/syncope-console/](http://localhost:8080/syncope-console/)

#### Configuration

To use the Console you need to perform additional steps:

* Update **/etc/default/tomcat** to make the configuration for the web app and the logger configuration
available.

Add the following lines:
~~~
# Apache Syncope Console configuration
JAVA_OPTS="${JAVA_OPTS} -Dsyncope.console.configuration=/etc/apache-syncope/configuration.properties \
 -Dlogback.configurationFile=/etc/apache-syncope/logback.xml"
~~~

* Update **/etc/apache-syncope/configuration.properties** and point it to Syncope Rest endpoint.

More information [https://issues.apache.org/jira/browse/SYNCOPE-244](here)

* Restart tomcat

~~~
    sudo service tomcat7 restart
~~~

Syncope Web Console is available at [http://localhost:8080/syncope-console/](http://localhost:8080/syncope-console/)

References
==========

* https://issues.apache.org/jira/browse/SYNCOPE-244
* https://help.ubuntu.com/community/Tomcat/PackagingWebapps
* http://tomcat.apache.org/tomcat-7.0-doc/config/context.html
* https://www.debian.org/doc/debian-policy/ch-controlfields.html
* https://cwiki.apache.org/confluence/display/SYNCOPE/Index
* https://github.com/tcurdt/jdeb