VERSION=1.0.0
PROXY_JAR=proxy-${VERSION}-jar-with-dependencies.jar
SAMPLE_CONFIG=./src/main/resources/kingfisherproxy.config.json
CONFIG_FILE=./config.json

default: build
.pthon: build

clean:
	mvn clean

build:
	mvn compile assembly:single

start: gen_config
	java -jar target/${PROXY_JAR} config.json

gen_config:
	test -s ${CONFIG_FILE} || cp ${SAMPLE_CONFIG} ${CONFIG_FILE}
