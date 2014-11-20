#!/bin/sh


###
### TODO
###  1. Generate with prompt
###  2. Be able to update config file via program
###

KEY=proxy.private.key
CSR=proxy.request.csr
CRT=proxy.certificate.crt
PFX=proxy_pkcs12.pfx
NAME=StubHub-Proxy
CONFIG=openssl.config

openssl genrsa -out ${KEY} 2048

# if version 0.9.8g
openssl.exe req -new -key ${KEY} -out ${CSR} -config ${CONFIG} 

# version 0.9.8h and later:
# openssl req -new -key <Key Filename> -out <Request Filename> -config C:\Openssl\bin\openssl.cfg


openssl x509 -req -days 3650 -in ${CSR} -signkey ${KEY} -out ${CRT} -extensions v3_req -extfile ${CONFIG} 

openssl.exe pkcs12 -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -export -in ${CRT} -inkey ${KEY} -out ${PFX} -name ${NAME}

cp proxy.private.key proxy.request.csr proxy_pkcs12.pfx ../src/main/resources/ -f
