#!/bin/bash

# script to reinstall myberkeley on calcentral-dev/calcentral-qa, while preserving content repository

if [ -z "$2" ]; then
    echo "Usage: $0 source_root logfile"
    exit;
fi

SRC_LOC=$1

INPUT_FILE="$SRC_LOC/.build.cf"
if [ -f $INPUT_FILE ]; then
  SLING_PASSWORD=`awk -F"=" '/^APPLICATION_PASSWORD=/ {print $2}' $INPUT_FILE`
  SHARED_SECRET=`awk -F"=" '/^SHARED_SECRET=/ {print $2}' $INPUT_FILE`
  X_SAKAI_TOKEN_SHARED_SECRET=`awk -F"=" '/^X_SAKAI_TOKEN_SHARED_SECRET=/ {print $2}' $INPUT_FILE`
  CONFIG_FILE_DIR=`awk -F"=" '/^CONFIG_FILE_DIR=/ {print $2}' $INPUT_FILE`
  ORACLE_USERNAME=`awk -F"=" '/^ORACLE_USERNAME=/ {print $2}' $INPUT_FILE`
  ORACLE_PASSWORD=`awk -F"=" '/^ORACLE_PASSWORD=/ {print $2}' $INPUT_FILE`
  ORACLE_URL=`awk -F"=" '/^ORACLE_URL=/ {print $2}' $INPUT_FILE`
  ORACLE_DB=`awk -F"=" '/^ORACLE_DB=/ {print $2}' $INPUT_FILE`
  OAE_DATABASE=`awk -F"=" '/^OAE_DATABASE=/ {print $2}' $INPUT_FILE`
  ORACLE_OAE_DB=`awk -F"=" '/^ORACLE_OAE_DB=/ {print $2}' $INPUT_FILE`
  ORACLE_OAE_USERNAME=`awk -F"=" '/^ORACLE_OAE_USERNAME=/ {print $2}' $INPUT_FILE`
  ORACLE_OAE_PASSWORD=`awk -F"=" '/^ORACLE_OAE_PASSWORD=/ {print $2}' $INPUT_FILE`
  MYSQL_PASSWORD=`awk -F"=" '/^MYSQL_PASSWORD=/ {print $2}' $INPUT_FILE`
else
  SLING_PASSWORD='admin'
  SHARED_SECRET='SHARED_SECRET_CHANGE_ME_IN_PRODUCTION'
  CONFIG_FILE_DIR=''
fi

LOG=$2
if [ -z "$2" ]; then
    LOG=/dev/null
fi
LOGIT="tee -a $LOG"

echo "=========================================" | $LOGIT
echo "`date`: Update started" | $LOGIT

echo | $LOGIT

cd $SRC_LOC/myberkeley
echo "`date`: Stopping sling..." | $LOGIT
mvn -B -e -Dsling.stop -P runner verify >>$LOG 2>&1 | $LOGIT
echo "`date`: Cleaning sling directories..." | $LOGIT
mvn -B -e -P runner -Dsling.purge clean >>$LOG 2>&1 | $LOGIT
rm -rf ~/.m2/repository/edu/berkeley
rm -rf ~/.m2/repository/org/sakaiproject

echo "`date`: Fetching new sources for myberkeley..." | $LOGIT
git pull >>$LOG 2>&1
echo "Last commit:" | $LOGIT
git log -1 | $LOGIT
echo | $LOGIT
echo "------------------------------------------" | $LOGIT

echo "`date`: Fetching new sources for 3akai-ux..." | $LOGIT
cd ../3akai-ux
git pull >>$LOG 2>&1
echo "Last commit:" | $LOGIT
git log -1 | $LOGIT
echo | $LOGIT
echo "------------------------------------------" | $LOGIT

cd ../myberkeley

STORAGE_FILES="$SRC_LOC/myberkeley/scripts/$OAE_DATABASE"
if [ -z "$CONFIG_FILE_DIR" ]; then
  echo "Not updating local configuration files..." | $LOGIT
else
  CONFIG_FILES="$SRC_LOC/myberkeley/configs/$CONFIG_FILE_DIR/load"
  echo "Updating local configuration files..." | $LOGIT

  # put the shared secret into config file
  SERVER_PROT_CFG=$CONFIG_FILES/org.sakaiproject.nakamura.http.usercontent.ServerProtectionServiceImpl.config
  if [ -f $SERVER_PROT_CFG ]; then
    grep -v trusted\.secret= $SERVER_PROT_CFG > $SERVER_PROT_CFG.new
    echo "trusted.secret=\"$SHARED_SECRET\"" >> $SERVER_PROT_CFG.new
    mv -f $SERVER_PROT_CFG.new $SERVER_PROT_CFG
  fi

  #put the X-SAKAI-TOKEN shared secret into Trusted Token Service config file
  TRUSTED_TOKEN_SERVICE_CFG=$CONFIG_FILES/org.sakaiproject.nakamura.auth.trusted.TrustedTokenServiceImpl.cfg
  if [ -f $TRUSTED_TOKEN_SERVICE_CFG ]; then
    grep -v sakai\.auth\.trusted\.server\.secret= $TRUSTED_TOKEN_SERVICE_CFG > $TRUSTED_TOKEN_SERVICE_CFG.new
    echo "sakai.auth.trusted.server.secret=$X_SAKAI_TOKEN_SHARED_SECRET" >> $TRUSTED_TOKEN_SERVICE_CFG.new
    mv -f $TRUSTED_TOKEN_SERVICE_CFG.new $TRUSTED_TOKEN_SERVICE_CFG
  fi

  #put the X-SAKAI-TOKEN shared secret into the Trusted Token Proxy Preprocessor config file
  TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG=$CONFIG_FILES/org.sakaiproject.nakamura.proxy.TrustedLoginTokenProxyPreProcessor.cfg
  if [ -f $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG ]; then
    grep -v sharedSecret= $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG > $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG.new
    echo "sharedSecret=$X_SAKAI_TOKEN_SHARED_SECRET" >> $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG.new
    mv -f $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG.new $TRUSTED_TOKEN_PROXY_PREPROCESSOR_CFG
  fi

  # Enable person attribute provision from Oracle.
  ORACLE_CONNECTION_CFG=$CONFIG_FILES/edu.berkeley.myberkeley.provision.OracleConnectionService.cfg
  if [ -f $ORACLE_CONNECTION_CFG ]; then
    grep -v datasource\.url= $ORACLE_CONNECTION_CFG > $ORACLE_CONNECTION_CFG.new
    echo "datasource.url=jdbc:oracle:thin:$ORACLE_USERNAME/$ORACLE_PASSWORD@$ORACLE_URL:$ORACLE_DB" >> $ORACLE_CONNECTION_CFG.new
    mv -f $ORACLE_CONNECTION_CFG.new $ORACLE_CONNECTION_CFG
  fi

  # Enable self-registration.
  FOREIGN_PRINCIPAL_CFG=$CONFIG_FILES/edu.berkeley.myberkeley.foreignprincipal.ForeignPrincipalServiceImpl.cfg
  if [ -f $FOREIGN_PRINCIPAL_CFG ]; then
    grep -v foreignprincipal\.secret= $FOREIGN_PRINCIPAL_CFG > $FOREIGN_PRINCIPAL_CFG.new
    echo "foreignprincipal.secret=$SHARED_SECRET" >> $FOREIGN_PRINCIPAL_CFG.new
    mv -f $FOREIGN_PRINCIPAL_CFG.new $FOREIGN_PRINCIPAL_CFG
  fi

  if [ $OAE_DATABASE == 'oracle' ]; then
    echo "Configuring for oracle" | $LOGIT
    # Fix Oracle OAE storage connection.
    if [ $ORACLE_OAE_PASSWORD ]; then
      SPARSE_CONFIG=$STORAGE_FILES/JDBCStorageClientPool.config
      if [ -f $SPARSE_CONFIG ]; then
        sed -e "s/ORACLE_OAE_DB/$ORACLE_OAE_DB/g" -e "s/ORACLE_OAE_USERNAME/$ORACLE_OAE_USERNAME/g" -e "s/ironchef/$ORACLE_OAE_PASSWORD/g" $SPARSE_CONFIG > $SPARSE_CONFIG.new
        mv $SPARSE_CONFIG.new $SPARSE_CONFIG
      fi
      JCR_CONFIG=$STORAGE_FILES/repository.xml
      if [ -f $JCR_CONFIG ]; then
        sed -e "s/ORACLE_OAE_DB/$ORACLE_OAE_DB/g" -e "s/ORACLE_OAE_USERNAME/$ORACLE_OAE_USERNAME/g" -e "s/ironchef/$ORACLE_OAE_PASSWORD/g" $JCR_CONFIG > $JCR_CONFIG.new
        mv $JCR_CONFIG.new $JCR_CONFIG
      fi
    fi
  elif [ $OAE_DATABASE == 'mysql' ]; then
    echo "Configuring for mysql" | $LOGIT
    # Fix MySQL password.
    if [ $MYSQL_PASSWORD ]; then
      SPARSE_CONFIG=$STORAGE_FILES/JDBCStorageClientPool.config
      if [ -f $SPARSE_CONFIG ]; then
        sed "s/ironchef/$MYSQL_PASSWORD/g" $SPARSE_CONFIG > $SPARSE_CONFIG.new
        mv $SPARSE_CONFIG.new $SPARSE_CONFIG
      fi
      JCR_CONFIG=$STORAGE_FILES/repository.xml
      if [ -f $JCR_CONFIG ]; then
        sed "s/ironchef/$MYSQL_PASSWORD/g" $JCR_CONFIG > $JCR_CONFIG.new
        mv $JCR_CONFIG.new $JCR_CONFIG
      fi
    fi  
  else
    echo "unknown database $OAE_DATABASE" | $LOGIT
  fi

  rm $SRC_LOC/myberkeley/working/load/*
  cp -f $CONFIG_FILES/* $SRC_LOC/myberkeley/working/load
fi

echo "`date`: Doing clean..." | $LOGIT
mvn -B -e clean >>$LOG 2>&1

echo "`date`: Starting sling..." | $LOGIT
mvn -B -e -Dsling.start -Dmyb.sling.config=$STORAGE_FILES -P runner verify >>$LOG 2>&1

# wait 2 minutes so sling can get going
sleep 120;

echo "`date`: Redeploying UX..." | $LOGIT
mvn -B -e -P runner -Dsling.install-ux -Dsling.password=$SLING_PASSWORD clean verify

echo | $LOGIT
echo "`date`: Reinstall complete." | $LOGIT
