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
