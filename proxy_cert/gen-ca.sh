#!/bin/sh


# ca
openssl req -x509 -config openssl-ca.cnf -newkey rsa:4096 -sha256 -out cacert.pem -outform PEM \
-passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=KingFisher CA"


touch index.txt

echo '01' > serial.txt


# server request
openssl req -config openssl-server.cnf -newkey rsa:2048 -sha256 -out server_example.com_cert.csr -outform PEM \
-passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=example.com"

# sign
openssl ca -config openssl-ca.cnf -batch -passin pass:123456 -policy signing_policy -extensions signing_req \
-out server_example.com_cert.pem -infiles server_example.com_cert.csr 


openssl pkcs12 -export -out server_example_cert.p12 -inkey serverkey.pem -in server_example.com_cert.pem \
-passin pass:123456 -passout pass:123456

cp server_example_cert.p12  ../src/main/resources/

