# $1 is domain
echo for domain: $1

pwd

#create the related folers

ca_folder=ca_sign_records

if [ ! -d $ca_folder ]; then
    mkdir $ca_folder
	touch $ca_folder/index.txt
	echo '01' > $ca_folder/serial.txt
fi

server_folder=server_cert_output

if [ ! -d $server_folder ]; then
    mkdir $server_folder
fi

serverFileName=$server_folder/$1_cert

echo ----------
echo step 1 : generate server request
echo ----------
# server request
export subjectAltName=DNS:$1,DNS:www.$1

openssl req -newkey rsa:2048 -sha256 -config openssl-server.cnf \
-passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=$1" \
 -keyout $serverFileName.key.pem -out $serverFileName.csr -outform PEM 

echo ----------
echo step 2 : sign it
echo ----------
# sign
openssl ca -config openssl-ca.cnf -batch -passin pass:123456 -policy signing_policy -extensions signing_req \
 -out $serverFileName.pem -infiles $serverFileName.csr

echo ----------
echo step 3 : convert from pem to pkcs12
echo ----------
openssl pkcs12 -export -inkey $serverFileName.key.pem -in $serverFileName.pem -out $serverFileName.p12 \
 -passin pass:123456 -passout pass:123456

echo ----------
echo step 4 : copy
echo ----------
echo $serverFileName.p12
cp $serverFileName.p12  ../src/main/resources/server_cert/
cp $serverFileName.p12  ../target/classes/server_cert/
cp $serverFileName.p12  ../server_cert/


echo ----------
echo the end
echo ----------


