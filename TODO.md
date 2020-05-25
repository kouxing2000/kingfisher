#TODO
- spring boot
- support multiple users
    - For every user, generate a unique root cert to prevent security risk
    - Identify user by IP? user agent? 

- Live Graphic Dashboard for network traffic

## Browser Plugin
- user login also support anonymous 
- flow
    - user: view the traffic, and select the one for mock
    - user: jump to rule edit, pre-fill the fields (match url, method, req header, 
    how response generated, can hard coded or can replaced)
    - user: save the rules
    - client code: send the rules to the proxy server
    - client code: enable the browser proxy
    - browser resend request to proxy, special header attached, to identify the user
    - server code: proxy identify the user, and based on his rules, mock response based on the rule
    

### refer 
https://developer.chrome.com/extensions/proxy
https://github.com/henices/Chrome-proxy-helper

https://developer.chrome.com/extensions/webRequest
https://github.com/jugglinmike/chrome-user-agent/blob/master/src/background.js

https://github.com/mitmproxy/mitmproxy
https://github.com/dutzi/tamper

#DONE
- upgrade little proxy https://github.com/adamfisk/LittleProxy

