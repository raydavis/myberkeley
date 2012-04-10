# Create a JAR to wrap the Solr "example" directory from Solr Subversion source.
myberkeley> mvn -e -P solrjar clean install

# Start a Solr server with OAE / CalCentral configurations.
# The Solr server directory will be "working/solr", and gets wiped by a "-Dsling.clean".
myberkeley> mvn -e -P solr -Dstart.solr clean install

# Configure CalCentral to use the remote Solr server.
# (This should be done automatically by Maven during "start.solr".)

# Start and stop CalCentral in the usual way.
# ONLY after CalCentral is stopped can you take the next step...

# Stop the Solr server (uses a Bash shell script):
myberkeley> mvn -e -P solr -Dstop.solr verify
