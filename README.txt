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

1. To clean out the old server environment including repository:
mvn -P runner -Dsling.clean clean

2) To clean the server of deployed and cached bundles while leaving repository and data intact:

mvn -P runner -Dsling.purge clean

3. To start the test server:

mvn -P runner -Dsling.start verify

4. To stop the test server:

mvn -P runner -Dsling.stop verify

5. To stop the server, reinstall everything except the repository data,
and restart (you'll need to have both myberkeley and 3akai-ux checked out):

./scripts/reinstall.sh source_root sling_password

6. To run the integration test against a fresh clean myberkeley instance:
./scripts/myberkeley_integration.sh

7. To install the Sling Explorer, which lets you browse the JCR repository:
mvn org.apache.sling:maven-sling-plugin:install-file -Dsling.file=./lib/org.apache.sling.extensions.explorer-1.0.0.jar -Dsling.user=admin -Dsling.password=$SLING_PASSWORD
Then you can go to http://localhost:8080/.explorer.html to see JCR nodes.

===

COOKBOOK - DEVELOPMENT

1. To build (or rebuild) sample code:

mvn clean install

2. To start loading client-side files from your local copy of
3akai-ux code rather than using the deployed version of 3akai-ux,
copy the "configs/dev/*.cfg" files to your "working/load" directory and
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
You will need ruby and ruby gems installed. Ruby 1.8.7 is required.
On windows set thru System Control Panel Advanced tab
Install the json and curb ruby gems

Instructions from Nakamura testscripts README --

In OSX 10.5 I needed to do the following.

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

8. To actually load data, from the myberkeley directory run
mvn -Dsling.loaddata integration-test
this will load data to the default server - http://localhost:8080/, using password - "admin, loading the default number of random users - 32 -
plus the users defined in ./myberkeley/integration/src/main/scripts/json_data.js.

or to load data to another server and/or another number of random users, run
mvn -Dsling.loaddata -Dloaddata.server=${server} -Dloaddata.password=${password} Dloaddata.numusers=${numusers} integration-test
where ${server} should be replaced with a full server URL such as https://portal-dev.berkeley.edu/
and ${numusers} should be replaced with the number you want, e.g. 50.

These choices would user the command line:
mvn -Dsling.loaddata -Dloaddata.server=https://portal-dev.berkeley.edu/ -Dloaddata.password=admin -Dloaddata.numusers=50 integration-test
mvn -Dsling.loaddata -Dloaddata.server=http://localhost:8080/ -Dloaddata.password=admin -Dloaddata.numusers=50 integration-test
NOTE: the trailing slash on the server URL is required

All users will be given the password "testuser".

9. The above user-load includes records keyed to the LDAP UIDs of MyBerkeley project
members, letting us test CAS authentication. When running MyBerkeley on your own
computer, you can log in at:
https://auth-test.berkeley.edu/cas/login?service=http://localhost:8080/dev/index.html
When running at portal-dev, you can use:
https://auth-test.berkeley.edu/cas/login?service=https://portal-dev.berkeley.edu/dev/index.html
