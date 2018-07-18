# cisco-cvp-streaming-transcription

Gateway media forking control and streaming transcription using Google Speech API.

Provides a simple microservice approach to controlling media forking at the gateway.
Its primary use is to allow a CVP application to send the caller media stream to an
external server for processing such as transcription or sentiment analysis.

From CVP Call Studio just use the built-in REST Client element to invoke the web app.
For testing use Postman or similar.
The call leg ID to use on the request URL is the CVP callid variable which is the same as
the VRU leg call GUID.

Operations:

      Start media forking from the gateway.
      Stop media forking.
      Combined media forking and transcription using streaming to Google Speech-To-Text service.

HTTP PUT request URLs:

      http://<host:port/path>/forking/<call_leg_ID>
      http://<host:port/path>/transcription/<call_leg_ID>

Request JSON body items for forking control:

      action          START or STOP
      calling         Target address and port
      called          Target address and port
      
      Examples
      
      {"action":"START",
	     "calling" : {"address": "10.61.196.19", "port": "16400"},
	     "called" : {"address": "10.61.196.19", "port": "16401"}}
       
      {"action":"STOP"}
      
Servlet initialisation parameters:

      GatewayHostList Comma separated list of gateway hostnames or IP addresses
      ListenAddress   IP address for receiving gateway XMF notifications
      ListenPort      IP port for receiving gateway XMF notifications and servlet requests
      ListenPath      Servlet URL path for gateway XMF notifications
      
Things still to be done:

      Complete documentation and explanatory notes.
      Handle session reconnection after break or initial failure to connect.
      Re-registration after keepalives missed and session closed at the gateway.
      Add configurable debug and logging especially to turn RTP stream diagnostic messaging on/off.
      Check safe across multiple concurrent servlet requests, synchronise transaction ID etc.


