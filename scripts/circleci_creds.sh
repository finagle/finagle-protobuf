cat <<EOF > credentials.properties
realm=Artifactory Realm
host=tendril.jfrog.io
user=$ARTIFACTORY_USER
password=$ARTIFACTORY_APIKEY
EOF

echo "Created cred file."
ls -la credentials.properties
head -4 credentials.properties
