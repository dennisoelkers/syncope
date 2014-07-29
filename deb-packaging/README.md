Apache Syncope - Debian Package
================================

Licensed under Apache License 2.0 - http://www.apache.org/licenses/LICENSE-2.0

Generates a Debian package for installing Apache Syncope with Apache Tomcat 7 servlet container from Debian.

## Requirements

The project can be built on the following system:

* Debian Wheezy or later
* OpenJDK-7
* Maven 3
* tomcat 7
* PostgreSQL Database (or any other database supported by Syncope)


Before you do stuff
===================

This project aims to make deploying Syncope reproducible and easy to administer. It's opinionated and Debian oriented.
It also can be improved so please submit pull requests.

### Components

~~~
   + console
   + core
~~~

#### Important paths

Like all good debian web applications, components will use:

 * /var/log/apache-syncope - to store logs
 * /var/lib/apache-syncope - to store extra files (connector bundles uder bundles directory)
 * /etc/apache-syncope     - to store application configuration
 * /usr/share/apache-syncope - to store application data that does not change

#### Console module

More details inside **console/README.md**

#### Core module

More details inside **core/README.md**

### Building

To build the project install openjdk7, maven and git to grab the sources. You also need to have PostgreSQL
installed and a database ready for Apache Syncope.

~~~
    git clone git@github.com:ieugen/apache-syncope-debian.git
    mvn clea install
    echo "Debian packages created inside target/"
~~~

References
==========

* https://help.ubuntu.com/community/Tomcat/PackagingWebapps
* http://tomcat.apache.org/tomcat-7.0-doc/config/context.html
* https://www.debian.org/doc/debian-policy/ch-controlfields.html
* https://cwiki.apache.org/confluence/display/SYNCOPE/Index
* https://github.com/tcurdt/jdeb

Credits
=======

The packaging was inspired by work done by Alexandru Piticaș [http://github.com/apiticas/apache-syncope-deb](here) .