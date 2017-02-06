# Introduction

## Background

It's always a best practice to have concatenation and minimization of resources (JavaScript, CSS) when deploy to production.
However it makes life harder when debug a issue at production.
In additions, people is likely to have some change in order to quick verify whether the fixing is right.


## Limitation of existing tools

- Why adding a parameter like `debug=true` to URL is not sufficient?
It enables to show un-minified version to help debug but can't allow to have a quick fix and verification.

- Why Charles (http://www.charlesproxy.com/), a commercial web debug proxy tool is not sufficient?
It's commercial and not programmable/highly config-able.


## Solution

Therefore, we'd like to create such tool, a proxy, to address this problem.

The tool will behavior as below

1. Started as standalone proxy server
2. It acts a middle-man proxy mostly. (Just fetch request from origin site and return the response)
3. When request match certain URL patterns, it respond with content from
    - Local files. Therefore, people will not only to serve un-minified file but also verify modification very quickly.
    - Mock services. In order to mock API requests from a predefined mock server.
    - Other similar environment. It could be useful while multiple QA boxes.

The tool will be able to start from command line and read a configuration file in order to figure out how to match URL to local files.

# Features

  - mock resource files (JS/CSS/Images) from local file or another URL
  - mock API response from local file or another URL
  - mock APE response with customized response header / body

# Build from source

  1. `mvn package` 
    - It will generate kingfisher/target/kingfisher-release-2.0.0.zip
  2. unzip the release zip file  `unzip kingfisher-release-2.0.0.zip`
  3. Modify the configuration file : `sample.config.xml` accordingly 
    - examples are provided, read it for details
  4. `sh run.sh` start the proxy!

# Install the root CA certification (Chrome)

  1. Open chrome://settings/
  2. Click show advanced settings
  3. Click button: manage certificates
  4. Drag the file (proxy_cert/cacert.pem) in.
  5. Find it (KingFisher CA), make it all trust
  6. Save

# Script Guidance

  1. Java Script
  2. embed variable:
     - context <br/>
      used to access http request <br/>
      [source](src/main/java/com/kingfisher/proxy/Context.java)
     - delegator <br/>
      used to generate response from local resource or remote URL resource <br/>
      [source](src/main/java/com/kingfisher/proxy/resolver/Delegator.java)
     - responseBuilder <br/>
      used to build response from sketch or existing response <br/>
      [source](src/main/java/com/kingfisher/proxy/util/HttpResponseBuilder.java)

# Other library worth to compare

- [WireMock](http://wiremock.org/)
- [hoxy](http://greim.github.io/hoxy/)
- [mock server](http://mock-server.com/)
- [browsermod proxy](http://bmp.lightbody.net/)
