now=`date "+%Y%m%d%H%M%S"`
LIMIT=32
ADMINUSER="admin"
ADMINPASS="3@k@1!"
DOMAIN="https://portal-qa.berkeley.edu/"
SCRIPT="/public/authprofile.tidy.infinity.json"
USERPREFIX="~testuser"
INDEX=0
TEXTFILE="portal-user-data"${now}".txt"

cd ~
for ((INDEX; INDEX <= LIMIT ; INDEX++))
do 
    curl -u ${ADMINUSER}:${ADMINPASS} ${DOMAIN}${USERPREFIX}${INDEX}${SCRIPT} >> ${TEXTFILE}
    printf ${INDEX}
done
