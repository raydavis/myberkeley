MyBerkeley Student Portal - Root Configuration

This bundle is based on the default Apache Sling root contents component
(org.apache.sling.launchpad.content), with the following changes:

- Component-configurable redirect path from the root URL.
- Component-configurable "admin" password.
  (Both of these are easily settable via normal Sling POST requests, but
  this lets them be set up before the HTTP service starts.)
- Adds a declarative service. This is purely a marker interface at present,
  but at least lets other services monitor the bundle's life cycle.
- Removes some of the Sling content used for testing.

OR via a curl POST:



