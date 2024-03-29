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
#export subjectAltName=DNS:$1,DNS:www.$1
#echo "subjectAltName $subjectAltName"

sed "s/example.com/$1/g" openssl-server.cnf > "$server_folder/openssl-server-$1.cnf"
# -config "$server_folder/openssl-server-$1.cnf" \

openssl req -newkey rsa:2048 -sha256 \
 -config "$server_folder/openssl-server-$1.cnf" \
 -passout pass:123456 -subj "/C=US/ST=California/L=San Francisco/O=Global Security/OU=IT Department/CN=*.$1" \
 -keyout $serverFileName.key.pem -out $serverFileName.csr -outform PEM 

if [ $? -eq 0 ]; then
    echo OK
else
    echo FAIL step 1
    exit 1
fi
# if you found error on Mac: 140735731753928:error:0E065068:configuration file routines:STR_COPY:variable has no value:/BuildRoot/Library/Caches/com.apple.xbs/Sources/libressl/libressl-22.50.2/libressl/crypto/conf/conf_def.c:573:line 39
# then run brew update && brew upgrade
# you will find openssl related log
#  openssl is keg-only, which means it was not symlinked into /usr/local,
#  because Apple has deprecated use of OpenSSL in favor of its own TLS and crypto libraries.
#  If you need to have openssl first in your PATH run:
#  echo 'export PATH="/usr/local/opt/openssl/bin:$PATH"' >> ~/.bash_profile

echo ----------
echo step 2 : sign it
echo ----------
# sign
openssl ca -config openssl-ca.cnf -batch -passin pass:123456 -policy signing_policy -extensions signing_req \
 -out $serverFileName.pem -infiles $serverFileName.csr

if [ $? -eq 0 ]; then
    echo OK
else
    echo FAIL step 2
    exit 1
fi

echo ----------
echo step 3 : convert from pem to pkcs12
echo ----------
openssl pkcs12 -export -inkey $serverFileName.key.pem -in $serverFileName.pem -out $serverFileName.p12 \
 -passin pass:123456 -passout pass:123456

if [ $? -eq 0 ]; then
    echo OK
else
    echo FAIL Step 3
    exit 1
fi

echo ----------
echo step 4 : copy
echo ----------
echo $serverFileName.p12
#cp $serverFileName.p12  ../src/main/resources/server_cert/
cp $serverFileName.p12  ../target/classes/server_cert/
cp $serverFileName.p12  ../server_cert/


echo ----------
echo the end
echo ----------


