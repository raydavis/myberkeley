now=`date "+%Y%m%d%H%M%S"`
ADMINUSER="admin"
ADMINPASS="3@k@1!"
DOMAIN="https://demo1.media.berkeley.edu/"
SCRIPT="/public/authprofile.tidy.infinity.json"
USERPREFIX="~"
INDEX=300846
LIMIT=300878
TEXTFILE="portal-user-data"${now}".txt"

cd ~
printf "extracting users\n"
for ((INDEX; INDEX <= LIMIT ; INDEX++))
do 
    /usr/bin/curl -k -u ${ADMINUSER}:${ADMINPASS} ${DOMAIN}${USERPREFIX}${INDEX}${SCRIPT} >> ${TEXTFILE}
    printf ${INDEX}
done
