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

6. To load test data against a fresh clean myberkeley instance:
./scripts/myberkeley_integration.sh
TO DO: This currently only verifies that test data can be loaded. Other
integration tests should be added to the project.

7. To install the Sling Explorer, which lets you browse the JCR repository:
mvn org.apache.sling:maven-sling-plugin:install-file -Dsling.file=./lib/org.apache.sling.extensions.explorer-1.0.0.jar -Dsling.user=admin -Dsling.password=$SLING_PASSWORD
Then you can go to http://localhost:8080/.explorer.html to see JCR nodes.

===

COOKBOOK - MYSQL

By default, OAE's Jackrabbit and Sparsemapcontent both store their data in a
Derby database. To use MySQL instead, do the following:

1. Initialize your MySQL database. (See "scripts/mysql/JDBCStorageClientPool.config"
for the default connection settings.)

2. Clean out your old server environment (including the Derby-based repository):
mvn -P runner -Dsling.clean clean

3. Edit the "scripts/mysql" files to match your MySQL settings (if you're not
using the defaults).

4. Start the server with the MySQL settings:

mvn -P runner -Dsling.start -Dmyb.sling.config=$PWD/scripts/mysql verify

===

COOKBOOK - DEVELOPMENT

1. To build (or rebuild) sample code:

mvn clean install

2a. Configure your server:
  cp -R configs/localhost working/

2b. To start loading client-side files from your local copy of
3akai-ux code rather than using the deployed version of 3akai-ux,
edit working/load/*FsResourceProvider*.cfg files so they point at your
local myberkeley directory (absolute paths).

3. To load sample user data, you will be running Ruby scripts via Maven.
You will need ruby and ruby gems installed. Ruby 1.8.7 is required.
On windows set thru System Control Panel Advanced tab
Install the json and curb ruby gems

Possibly obsolete instructions from old Nakamura testscripts README:

In OSX 10.5 I needed to do the following.

sudo gem update
sudo gem install json
sudo gem install curb

If you are running OS X 10.6, the following commands work:
sudo gem update --system
sudo gem update
sudo gem install json
sudo env ARCHFLAGS="-arch x86_64" gem install curb

On Windows, see the instructions at:
https://confluence.sakaiproject.org/x/9IIpB

4. To actually load data, start the server. Then, from the
myberkeley directory, run

mvn -Dsling.loaddata integration-test

this will load data to the default server - http://localhost:8080/, using password "admin", loading the users defined in ./myberkeley/integration/src/main/scripts/json_data.js and ucb_data_loader.rb.

or to load data to another server, run
mvn -Dsling.loaddata -Dloaddata.server=${server} -Dloaddata.password=${password} integration-test
where ${server} should be replaced with a full server URL such as https://calcentral-dev.berkeley.edu/
NOTE: the trailing slash on the server URL is required

All users will be given the password "testuser".

5. The above user-load includes records keyed to the LDAP UIDs of MyBerkeley project
members, letting us test CAS authentication. When running MyBerkeley on your own
computer, you can log in at:
https://auth-test.berkeley.edu/cas/login?service=http://localhost:8080/dev/index.html
When running at calcentral-dev, you can use:
https://auth-test.berkeley.edu/cas/login?service=https://calcentral-dev.berkeley.edu/dev/index.html

