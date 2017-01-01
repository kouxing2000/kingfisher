# $1 is domain
echo for domain: $1

echo generate server request
echo ----------
# server request
export subjectAltName=DNS:$1,DNS:www.$1

openssl req -newkey rsa:2048 -sha256 -config openssl-server.cnf \
-passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=$1" \
 -out server_$1_cert.csr -outform PEM 

echo sign it
echo ----------
# sign
openssl ca -config openssl-ca.cnf -batch -passin pass:123456 -policy signing_policy -extensions signing_req \
-out server_$1_cert.pem -infiles server_$1_cert.csr 

echo convert from pem to pkcs12
echo ----------
openssl pkcs12 -export -inkey serverkey.pem -in server_$1_cert.pem -out server_$1_cert.p12 \
-passin pass:123456 -passout pass:123456


echo copy then done
echo -----
cp server_$1_cert.p12  ../src/main/resources/


