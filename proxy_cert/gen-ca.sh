#!/bin/sh


# ca
openssl req -x509 -config openssl-ca.cnf -days 9999 -newkey rsa:4096 -sha256 -out cacert.pem -outform PEM \
-passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=KingFisher CA"




