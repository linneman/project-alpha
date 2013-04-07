# Project Alpha - An Open Source Dating Platform

Project alpha is an internet dating platform which is completely based on free software. It is written from ground up in Clojure and Clojurescript and follows consequently a design principle where most of the application logic is executed on the client side within the webbrowser.

On contrast to other Clojurescript based applications we do not try to hide DOM manipulation behind a complex framwork. Instead all html code is delivered at once and switching between html pages is done in a simple and straightforward way by fading in and fading out corresponding div elements. So all of the application content is provided as html and css which is still the most common way of web development today and content designers are not necessarily required to learn Clojurescript. The advantage of this approach is a signifcantly improved user experience when the application has been completely loaded. Because all page rendering is realized on the client there is no need to generate and transfer html code from the server anymore so the webpage reacts more directly on users requests. All data which is transfered between client and server is JSON encoded. The JSON format can be considered as the servers API which can be accessed by 3rd party applications e.g. for smartphone apps, too.

Our belief is that for the given reasons the choosen approach could become a protoype for web development in a much broader sense. We are looking forward to get feedback. The following paragraphs provide a very densed description how to get the application up and running. Be aware that a fundamental knowledge of the Clojure programming environment and libraries is required for doing this. If you are new to Clojure based web development please refer to the following websites for additional useful information:

* http://clojre.org - the Clojure programming language
* https://github.com/ring-clojure/ring - the clojure adapter for the Jetty webserver
* https://github.com/weavejester/compojure - the routing library for ring
* https://github.com/clojure/clojurescript - Clojurescript
* https://github.com/jramb/JanusClojure - a tutorial using a simple demo web application
* http://clojurescriptone.com - an application framework build on Clojure and Clojurescript.


## Quick Start

### Downloading the Source Code
Clojure uses the package management tool [Leiningen](https://github.com/technomancy/leiningen) as a defacto standard now. Leiningen is initially provided as a single script file which will download the rest of its implementation and all libraries the application depends on from the web.

* Download the script leiningen from [Github](https://github.com/technomancy/leiningen) and make it accessible to your shell
* Type the following command from your shell

        lein self-install

* Clone the project form this repository

        git clone git://github.com/linneman/project-alpha.git
        git submodule init
        git submodule update
        lein deps

### Setup OpenGeo Databases
This application uses MySQL version 5.5 or later as database together with OpenGeoDB which is required for distance calcuations between zip locations. Make sure that you have a MySQL instance up and running. All actions to setup OpenGeoDB are summarized in the following steps:

* Download database tables with German ZIP codes from [opengeodb](http://fa-technik.adfc.de/code/opengeodb). Only the files [DE.sql](http://fa-technik.adfc.de/code/opengeodb/DE.sql), [opengeodb\_begin.sql](http://fa-technik.adfc.de/code/opengeodb/opengeodb-begin.sql) and [opengeodb\_end.sql](http://fa-technik.adfc.de/code/opengeodb/opengeodb-end.sql) are required.
* Create the database table "opengeodb_de" within MySQL
* Patch the file [opengeodb_begin.sql](http://fa-technik.adfc.de/code/opengeodb/opengeodb-begin.sql) for usage with MySQL 5.5 by replacing all strings "TYPE=InnoDB" with "ENGINE=InnoDB"
* Import the geodata using the following commands:

        cat opengeodb-begin.sql | mysql --user=root --password=secret opengeodb_de
        cat DE.sql | mysql --user=root --password=secret opengeodb_de
        cat opengeodb-end.sql | mysql --user=root --password=secret opengeodb_de


### Compile Clojurescript Code

The Javascript code running in the browser is generated from Clojurescript and needs to be compiled initially. Type

    lein cljsbuild once

to compile both, a debug and release version. Be aware that the build mechanism does not always detect all dependencies in case you do changes on the client side so its always a good idea to prepend a 'clean' command (replace once by clean) to ensure that everything is build correctly.

### Setup Database Tables for Project Alpha
Project alpha needs a SQL database and a SQL user which has granted access rights to it. Both can be created by the MySQL client application or some 3rd party tools as phpmyadmin. After you have done that follow the steps below to create the database tables for the appliation.

* Insert MySQL ports, databases and sql user in src/project-alpha-server/local_settings.clj
* Start the Clojure REPL by typing

        lein repl
* Invoke Method for table creation by entering:

        (create-all-tables)

### Start the Server application

You can now start up the serverside of the application by entering

    (start-server)

Alternatively the server side of the application can be started directly from the shell by entering

    lein run


##  REPL Based Web Development

The so called Read-Eval-Print-Loop, in short REPL, is a very powerful way of changing Clojure code while its is executed. This is in fact one of most powerful concepts in software development and allows to try and apply changes from the program editor directly while a program is still running. You can connect your favorite editor to both, the client side running compiled Clojurescript code within the Webbrowser and the server side running Clojure code on top of the JVM. We give a very brief summary of the required steps to do this in the following. You a required to have Emacs installed on your machine and to setup it appropriately. You will need the Emacs extensions [slime](http://common-lisp.net/project/slime), [clojure-mode](https://github.com/technomancy/clojure-mode) and [further extensions for the Clojurescript REPL](https://github.com/brentonashworth/one/wiki/Emacs) provided from Brenton Ashworth. Alternatively you can clone the Emacs setup which was used for the most of the development in this project from [Github](https://github.com/linneman/emacs-setup).

### Server Side
* make sure the SQL server is up and running
* start Emacs, with the exception of the web browser the following commands are all executed from the Emacs environment
* start an ANSI-Terminal with meta-x followed by "ansi-term", form there and enter the commands after the colon:

        sh$: cd project-alpha (project's root directory)
        sh$: lein run
* open file src/project-alpha-server/app/core.clj
* connect slime with meta-x followed by "slime-connect"
* emacs should show two buffers now - 'core.clj' and the 'slime-repl'. If not setup the frames manually. Refer to the emacs documentation how to do this.
* change the namespace to core.clj by hitting ctrl-x meta-p. Set 'core.clj' as active buffer first
* you can now evaluate the Clojure expressions in core.clj the cursor after the expression and pressing crtl-x-crtl-e
* More information is available on the [swank project](https://github.com/technomancy/swank-clojure)

### Client Side
* start a web browser (a recent version of firefox, chrome, safari or opera is required)
* within Emacs (same instance as in server) follow these steps:
* open a shell with meta-x followed by "shell", from there enter the commands after the colon:

        sh$: cd project-alpha (project's root directory)
        sh$: lein trampoline cljsbuild repl-listen
* load the webpage http://localhost:3000/repl.html within your web browser, your client repl should now be operational which you should check e.g. like this:

        ClojureScript:cljs.user> (+ 1 1)
        2
* If the repl does not react try to reload the page
* Setup Emacs to display a clojurescript buffer e.g. 'src/project-alpha-client/app/status.cljs' and the buffer 'shell' (cljs-repl)
* put the cursor behind the first 'ns'-expression and switch namespace to this expression by hitting 'crtl-c e'
* you can now evaluate other clojurescript expressions from this buffer by 'crtl-c e'

## Licence
This implementation code stands under the terms of the
[GNU General Public Licence](http://www.gnu.org/licenses/gpl.html).

April 2013, Otto Linnemann

## Resources and links
Thanks to all the giants whose shoulders we stand on. And the giants theses giants stand on...
And special thanks to Rich Hickey (and the team) for Clojure. Really, thanks!

* Clojure: http://clojure.org
* Cojurescript: https://github.com/clojure/clojurescript
* Leiningen: https://github.com/technomancy/leiningen
