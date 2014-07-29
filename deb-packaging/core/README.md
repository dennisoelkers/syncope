Apache Syncope Core WebApp - Debian Package
==============================================

Licensed under Apache License 2.0 - http://www.apache.org/licenses/LICENSE-2.0

Generates a Debian package for installing Apache Syncope Core with Apache Tomcat 7 servlet container from Debian.
Source available at [https://github.com/ieugen/apache-syncope-debian](https://github.com/ieugen/apache-syncope-debian).

### Requirements

The project can be built on the following system:

* Debian Wheezy or later
* OpenJDK-7
* Maven 3
* tomcat 7

#### Core module

  Repackages official Apache Syncope Core war to include some customisations - mainly the logging configuration.
It also includes **configuration.properties** which provides the link to Syncope Core webapp.
Include here your custom configuration - that can't be changed after build.

#### Building

To build the project install openjdk7, maven and git to grab the sources.

** Note: ** Before building you might wish to change the default Administrator user name and password inside
**src/main/resources/security.properties**. See more details on the
[https://cwiki.apache.org/confluence/display/SYNCOPE/Set+administrator+credentials](official page).

~~~
    git clone git@github.com:ieugen/apache-syncope-debian.git
    cd core
    mvn clean install
    sudo dpkg -i target/apache-syncope-core-$version.deb
~~~

#### Configuration

To use the Core you need to perform additional steps:

* Update **/etc/default/tomcat** to make the configuration for the web app and the logger configuration
available.

Add the following lines:
~~~
# Apache Syncope Core configuration
JAVA_OPTS="${JAVA_OPTS}  -Dlogback.configurationFile=/etc/apache-syncope/logback.xml"
~~~

* Configure a DataSource in Tomcat for Syncope Core

For PostgreSQL you have to install the driver and make it available to Tomcat web apps:
~~~
   sudo aptitude install libpostgresql-jdbc-java
   echo "Make database diver available to web application (all of them)"
   sudo ln -s /usr/share/java/postgresql-jdbc4.jar /usr/share/tomcat7/lib/
~~~

* Create **syncope** databse a database and user (you must have PostgreSQL installed allready).
~~~
   sudo aptitude install postgresql
   sudo SYNCOPE_USER="syncope" SYNCOPE_PASS="pass#1234" sh /usr/share/apache-syncope/dbinit-postgresql.sh
~~~

* Configure a **javax.sql.DataSource** and expose it in JNDI as **jdbc/syncopeDataSource**
You can do that by editing the Syncope Core web application descriptor: **/etc/tomcat7/Catalina/localhost/syncope-core.xml** .

It is enough to uncomment and update the sample configuration:
~~~
    <Resource name="jdbc/syncopeDataSource" auth="Container"
              type="javax.sql.DataSource" driverClassName="org.postgresql.Driver"
              url="jdbc:postgresql://127.0.0.1:5432/syncope"
              username="syncope" password="password" maxActive="20" maxIdle="10"
              maxWait="-1"/>
~~~


* Restart tomcat
~~~
    sudo service tomcat7 restart
~~~

References
==========

* https://issues.apache.org/jira/browse/SYNCOPE-244
* https://help.ubuntu.com/community/Tomcat/PackagingWebapps
* http://tomcat.apache.org/tomcat-7.0-doc/config/context.html
* https://www.debian.org/doc/debian-policy/ch-controlfields.html
* https://cwiki.apache.org/confluence/display/SYNCOPE/Index
* https://github.com/tcurdt/jdeb
* https://cwiki.apache.org/confluence/display/SYNCOPE/Set+administrator+credentials
