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
    - Other similar enviornment. It could be useful while multiple QA boxes.

The tool will be able to start from command line and read a configuration file in order to figure out how to match URL to local files.

# Features

  - mock resource files (JS/CSS/Images) from local file or another URL
  - mock API response from local file or another URL
  - mock APE response with customized response header / body

# Build from source

  1. `make build` or `mvn compile assembly:single`
  2. Change the `config.json` at root dir per your case. (read config.json for details)
  3. `make start` or `java -jar target/kingfisher-1.0.0-SNAPSHOT-jar-with-dependencies.jar config.json`


*NOTE*: read the `Makefile` for details if those two does not work well. ;)

# Other library worth to compare

- [WireMock](http://wiremock.org/)
- [hoxy](http://greim.github.io/hoxy/)
- [mock server](http://mock-server.com/)
- [browsermod proxy](http://bmp.lightbody.net/)
