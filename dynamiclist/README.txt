WORKING NOTES

OVERVIEW
========

Three content areas come into play here:

* A Dynamic List Context definition (corresponding to a group of students and their advisors)
is added to storage under "/var". This defines what criteria can be used for a Dynamic List.
Only the group's advisors should be given read access to this node.

The initial one is at "/var/myberkeley/dynamiclists/myb-advisers-ced".

* Relevant criteria for each student are stored in a node under the student's home:
"/~STUDENT_USER_ID/_myberkeley-demographic".
This node should be made inaccessible to all accounts, including advisors. It's
basically only used to trigger the Solr index updates. (We might even decide to
cut out the middleman and add to Solr directly.)

* Dynamic List instances (as authored by advisors in MyBerkeley) must include a Context ID
and a JSON string describing the list's criteria (described below). These can then be passed
to the DynamicListQueryServlet and to the DynamicListService.


HOW IS THE QUERY IMPLEMENTED?
=============================

My initial implementation is based on local Sparse storage + Solr search. I branched ieb/solr
just to add two indexed fields to "schema.xml":

   <!-- MyBerkeley additions -->
   <field name="myb-demographics" type="string" indexed="true" stored="false" multiValued="true"/>
   <field name="myb-context" type="string" indexed="true" stored="false" multiValued="false"/>

Alternatively, an implementation might be based on local Sparse storage + Sparse property-based
search. This would require branching ieb/sparsemapcontent to add the new fields to the
"index_cols" table for each DDL setup.

And alternatively, an implementation might disregard Sparse storage altogether and use an
external provider or a local SQL table.

OAE deployers will need more sustainable ways to add new functionality to the system.
One promising approach might be to leverage Solr's multi-core capability to load locally-configured
indexes.


DYNAMIC LIST CONTEXT
====================

A starting Dynamic List Context node is created at:
  /var/myberkeley/dynamiclists/myb-advisers-ced

Its ACL sets read-access for CED advisors.


TO DO
====

1. Add integration tests to check security.


TEST RUN
========

# Create a personal demographic profile for a student. This would be done by our data import.
curl -u admin:admin http://localhost:8080/~300847.myb-demographic.html \
  -F myb-demographics="/colleges/CED/standings/grad" \
  -F myb-demographics="/colleges/CED/standings/grad/programs/LAND ARCH AND ENV PLAN"

# Take a look.
curl -u admin:admin http://localhost:8080/~300847/_myberkeley-demographic.tidy.2.json
{
  "sling:resourceType": "myberkeley/personalDemographic",
  "_created": 1300987141413,
  "_id": "rWSSkanQU5xpozVklih",
  "_lastModifiedBy": "admin",
  "myb-demographics": [
    "/colleges/CED/standings/grad",
    "/colleges/CED/standings/grad/programs/LAND ARCH AND ENV PLAN"
  ],
  "_lastModified": 1301009856381,
  "_createdBy": "admin",
  "_path": "a:300847/_myberkeley-demographic"
}

# Here's a sample query, somewhat like what we'd retrieve from a named Dynamic List.
curl -g -u admin:admin "http://localhost:8080/var/myberkeley/dynamiclists/myb-advisers-ced.json?criteria={OR:[\"/colleges/CED/standings/grad/programs/ARCHITECTURE\",\"/colleges/CED/standings/grad/programs/LAND%20ARCH%20AND%20ENV%20PLAN\"]}"

# And the results:
{"count":1}

# The log file is a bit more interesting:
... DynamicListQueryServlet For criteria = ... and context = myb-advisers-ced, user Ids = [300847]

###

That query wasn't terribly easy to read. Pretty-printed, the "criteria" parameter was:
  {
    OR: [
      "/colleges/CED/standings/grad/programs/ARCHITECTURE",
      "/colleges/CED/standings/grad/programs/LAND ARCH AND ENV PLAN"
    ]
  }
Which means "Match any of the following criteria."

For all undergrads, the criteria would just be:
  "/colleges/CED/standings/undergrad"

Future clauses might include:
  /colleges/CED/standings/grad/jobs/GSI
  /colleges/CED/standings/undergrad/levels/freshman
  /specialprograms/DSP

So, for example, to look for undergraduates majoring in "Architecture" or "Landscape
Architecture" and for GSRs in the equivalent programs, the criteria might look like:
  {
    OR: [
      "/colleges/CED/standings/undergrad/majors/ARCHITECTURE",
      "/colleges/CED/standings/undergrad/majors/LANDSCAPE ARCH",
      {
        AND: [
          "/colleges/CED/standings/grad/jobs/GSR",
          {
            OR: [
              "/colleges/CED/standings/grad/programs/ARCHITECTURE",
              "/colleges/CED/standings/grad/programs/LAND ARCH AND ENV PLAN"
            ]
          }
        ]
      }
    ]
  }

The hierarchical paths are used to (hopefully) give us more flexible integration options.
They could be parsed by an external group-and-role provider, for example. In Solr, they
give us the option of querying "all" via wildcards:

  /colleges/CED/*
