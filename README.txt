*** WORK IN PROGRESS ***

STRUCTURE

* app : The MyBerkeley project's own OSGi components. Currently this is just a bit of sample code.
* launchpad : A tailored version of the Sakai 3 application, consisting of a selection of standard Sakai 3 components, plus any of our customized components, plus our MyBerkeley-specific components.
* integration : A utility submodule to handle server control, data loading, etc.

===

COOKBOOK - SERVER CONTROL

Although this directory contains the project source code, you do not have to
actually build a new version of the application to run it. If you are on a
shared server environment, it's preferable to use a shared build that has
been stored in our project's Maven respository.

As a convenience, the "runner" profile can be used to download, stop, and
start an existing binary of the MyBerkeley application.

1. To clean out the old server environment:

mvn -P runner -Dsling.clean clean

3. To start the test server:

mvn -P runner -Dsling.start verify

4. To stop the test server:

mvn -P runner -Dsling.stop verify

===

COOKBOOK - DEVELOPMENT

1. To build (or rebuild) sample code:

mvn clean install

2. To start loading client-side files from your local copy of
3akai-ux code rather than using the deployed version of 3akai-ux,
copy the "sample/*.cfg" files to your "working/load" directory and
edit them to point to your local copy.

3. To create some test content while the server is running:

curl -u admin:admin -F "sling:resourceType=myberkeley/scriptedsample" \
  -F title="Some Title"  http://localhost:8080/content/firstscriptedsample

For more on RESTful content management through Sling, see:
http://sling.apache.org/site/manipulating-content-the-slingpostservlet.html

4. To try the Groovy script:

curl http://localhost:8080/content/firstscriptedsample.json

To try the server-side Javascript script:

curl http://localhost:8080/content/firstscriptedsample.html

For more on server-side scripting in Sling, see:

http://cwiki.apache.org/SLING/scripting-variables.html

5. To try the servlet:

curl http://localhost:8080/content/firstscriptedsample.servletized.json

For more on servlets:
http://sling.apache.org/site/servlets.html

For more on URL mapping:
http://sling.apache.org/site/url-decomposition.html

6. To try a normal client-side-only HTML + JavaScript view of some initially loaded content:

curl http://localhost:8080/myberkeley/index.html

For more on initial content loading:
http://sling.apache.org/site/content-loading-jcrcontentloader.html

7. To load sample user data, you will be running ruby scripts via maven.
You will need ruby and ruby gems installed. 
On Unix in your .profile file - export RUBYOPT=rubygems
On windows set thru System Control Panel Advanced tab 
Install the json and curb ruby gems

Instructions from Nakamura testscripts README --

n OSX 10.5 I needed to do the following.

sudo gem update 
sudo gem install json 
sudo gem install curb

If you are running OS X 10.6, the following commands work:
sudo gem update --system
sudo gem update
sudo gem install json
sudo env ARCHFLAGS="-arch x86_64" gem install curb

On Windows do either of the above, depending on whether you're running 64 bit windows
Run a command window as Administrator then
gem update 
gem install json 
gem install curb

or 

gem update --system
gem update
gem install json
set ARCHFLAGS="-arch x86_64" 
gem install curb

8. To actually loaddate, from the myberkeley directory run
mvn -Dsling.loaddata integration-test
to load 20 random users, the default number plus the users defined in ./myberkeley/integration/src/main/scripts/json_data.js.

or run
mvn -Dsling.loaddata -Dloaddata.numusers=5 integration-test
to load another number, in this case 5, of random users