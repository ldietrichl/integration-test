#!/bin/sh
set -eu
cd /runtime/tls
if [ -f truststore.p12 ] && [ -f client.p12 ]; then
    printf '%s\n' 'Existing local CA retained.'
    exit 0
fi
umask 077
printf '%s\n' '[req]' 'distinguished_name=dn' 'x509_extensions=ca_extensions' '[dn]' \
    '[ca_extensions]' 'basicConstraints=critical,CA:TRUE' 'keyUsage=critical,keyCertSign,cRLSign' > ca.cnf
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.pem -days 7 \
    -subj '/CN=Scheduler disposable local CA' -config ca.cnf
openssl req -new -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj '/CN=tls-proxy'
printf '%s\n' 'subjectAltName=DNS:tls-proxy,DNS:localhost,IP:127.0.0.1' 'extendedKeyUsage=serverAuth' > server.ext
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out server.pem -days 7 -extfile server.ext
openssl req -new -newkey rsa:2048 -nodes -keyout client.key -out client.csr -subj '/CN=scheduler-local-test-client'
printf '%s\n' 'extendedKeyUsage=clientAuth' > client.ext
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out client.pem -days 7 -extfile client.ext
openssl pkcs12 -export -in client.pem -inkey client.key -certfile ca.pem -out client.p12 -passout env:LOCAL_DB_PASSWORD
keytool -importcert -noprompt -alias scheduler-local-ca -file ca.pem -keystore truststore.p12 \
    -storetype PKCS12 -storepass:env LOCAL_DB_PASSWORD
