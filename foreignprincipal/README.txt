WORK IN PROGRESS

This bundle implements a dynamic group named "sakai:foreignPrincipal". Any user
who has successfully authenticated but who does not yet have a matching
Jackrabbit User record will be treated as a member of this group when checking
ACLs.

EXAMPLE

alias curll='curl -i -b /tmp/cookieTmp -c /tmp/cookieTmp -L'

# By default, "sites" is open to the anonymous public.
curl http://localhost:8080/sites.json
# {"sling:resourceType":"sakai/sites",...}

# Instead, let's keep it to ourselves.
curl -u admin:admin -F principalId=anonymous -F privilege@jcr:read=denied http://localhost:8080/sites.modifyAce.html

curl -i http://localhost:8080/sites.json
# HTTP/1.1 404 Not Found

# Using SSO, let's log into Nakamura as a user with no Jackrabbit record.
# (For now, just take my word that this works.)
castest

# We have a session.
curll http://localhost:8080/system/sling/info.sessionInfo.json
{"userID":"212380","workspace":"default"}

# But we do not yet have a matching Jackrabbit Authorizable.
curl -u admin:admin http://localhost:8080/system/userManager/user/212380.json
# Page Not Found

# We can see "sites" because we're not anonymous.
curll -i http://localhost:8080/sites.json
# HTTP/1.1 200 OK

# Lock it down.
curl -u admin:admin -F principalId=sakai:foreignPrincipal -F privilege@jcr:read=denied http://localhost:8080/sites.modifyAce.html

# Try again.
curll -i http://localhost:8080/sites.json
# HTTP/1.1 404 Not Found

*******************

From the list:

Jackrabbit and Sling are perfectly fine with authenticating and authorizing a
Principal that doesn't have a corresponding Jackrabbit User or Group object.
(Although you'd better not try adding that Principal to a Jackrabbit Group.) In
a deployment of the current Sakai 3 code, when you authenticate but don't have a
corresponding User node, what breaks is the UX's call to the MeServlet: Nakamura
needs to store user-specific resources (properties, a profile, a home folder,
and so on), and we manage that via a Jackrabbit Authorizable. The UX's
"index.js" calls the MeServlet to see if the user has logged in, and if there's
no user ID in the response, then it shows the home page with any passed error
message. However, when the MeServlet tries to get an Authorizable it doesn't
check for null, and therefore throws an exception.

OK, so my first use case is the Berkeley student portal pilot: You log in
through CAS, but if you aren't a member of the targeted pilot population, you
get redirected to a "so sorry, try again next month" page.

At first I thought of this as "vetoing authentication." But that's not really
the case, because the user is completely logged in so far as the CAS server is
concerned, and we're required to give the user a way to log out of CAS if we
were responsible for logging the user in. Moreover, we can't just immediately
boot the user out to the CAS logout URL. (We don't have a way of making the CAS
server nicely explain what just happened, and we can't be sure that we were
responsible for the CAS login -- the user might have logged into CAS sometime
before going to the portal.) The only way to meet our local obligations is to
fully authenticate the user, and then show them a page that explains the issue
and provides a logout button.

My second use case is the one mentioned in the Sling list's discussion about
OpenID integration, and the one I often see with Shibboleth integrations:
restricting the self-registration page to externally authenticated users. This
again is a case where the user must be authenticated (rather than vetoed), so
that the self-registration page can be protected against "anonymous".

Put these together, and the right plug-in point seems like some "handle unknown
user" servlet or filterish thing that can be triggered either before the
MeServlet's current code or after the MeServlet returns something a bit smarter
than a null pointer exception.

So how would an authentication handler which knows some personal attributes
convey them to the "handle unknown user" thing to decorate a new user record?
One conveyance might be as the attributes on the Credentials object which get
copied to Jackrabbit Session attributes. (Sling's current OpenID integration
stores the OpenIdUser in a Credentials attribute, and might soon use it to
convey OpenID AX attributes.)

