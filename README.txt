*** WORK IN PROGRESS ***

STRUCTURE

* app : The MyBerkeley project's own OSGi components. Currently this is just a bit of sample code.
* launchpad : A tailored version of the Sakai 3 application, consisting of a selection of standard Sakai 3 components, plus any of our customized components, plus our MyBerkeley-specific components.
* integration : A utility submodule to handle server control, data loading, etc.

===

COOKBOOK

1. To build (or rebuild) sample code:

mvn clean install

2. To clean out the old test server environment:

mvn -Dsling.clean clean

3. To start the test server:

mvn -Dsling.start verify

4. To stop the test server:

mvn -Dsling.stop verify

5. To create some test content:

curl -u admin:admin -F "sling:resourceType=myberkeley/scriptedsample" \
  -F title="Some Title"  http://localhost:8080/content/firstscriptedsample

For more on RESTful content management through Sling, see:
http://sling.apache.org/site/manipulating-content-the-slingpostservlet.html

6. To try the Groovy script:

curl http://localhost:8080/content/firstscriptedsample.json

To try the server-side Javascript script:

curl http://localhost:8080/content/firstscriptedsample.html

For more on server-side scripting in Sling, see:

http://cwiki.apache.org/SLING/scripting-variables.html

7. To try the servlet:

curl http://localhost:8080/content/firstscriptedsample.servletized.json

For more on servlets:
http://sling.apache.org/site/servlets.html

For more on URL mapping:
http://sling.apache.org/site/url-decomposition.html

8. To try a normal client-side-only HTML + JavaScript view of some initially loaded content:

curl http://localhost:8080/myberkeley/index.html

For more on initial content loading:
http://sling.apache.org/site/content-loading-jcrcontentloader.html
