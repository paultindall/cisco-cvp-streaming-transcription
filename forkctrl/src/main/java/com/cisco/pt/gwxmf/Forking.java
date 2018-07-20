package com.cisco.pt.gwxmf;

/*
 * ===================================================================================
 * IMPORTANT
 *
 * This sample is intended for distribution on Cisco DevNet. It does not form part of
 * the product release software and is not Cisco TAC supported. You should refer
 * to the Cisco DevNet website for the support rules that apply to samples published
 * for download.
 * ===================================================================================
 *
 * GATEWAY MEDIA FORKING SERVLET
 * 
 * Provides a simple microservice approach to controlling media forking at the gateway.
 * Its primary use is to allow a CVP application to send the caller media stream to an
 * external server for processing such as transcription or sentiment analysis.
 *
 * HTTP PUT request URLs:
 *      http://<host:port/path>/forking/<call_leg_ID>
 *      http://<host:port/path>/transcription/<call_leg_ID>
 *
 * Request JSON body items for forking control:
 *      action          START or STOP
 *      callerStream    Target address and port
 *      ivrStream       Target address and port
 *      
 * Servlet initialisation parameters:
 *      GatewayHostList Comma separated list of gateway hostnames or IP addresses
 *      ListenAddress   IP address for receiving gateway XMF notifications
 *      ListenPort      IP port for receiving gateway XMF notifications and servlet requests
 *      ListenPath      Servlet URL path for gateway XMF notifications
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 11 May 2018 Initial version, for PoC, not hardened
 * -----------------------------------------------------------------------------------
 */

import com.cisco.schema.cisco_xmf.v1_0.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


@WebServlet(name = "Forking",
            urlPatterns = {"/forking/*", "/transcription/*"},
            loadOnStartup = 1,
            initParams =
            {
//              @WebInitParam(name = "ListenPath", value = "/xmfnotify")
                @WebInitParam(name = "GatewayHostList", value = "rmlab-cube2"),
                @WebInitParam(name = "ListenAddress", value = "10.61.245.91"),
                @WebInitParam(name = "ListenPort", value = "9090")
            })


public class Forking extends HttpServlet {
    
    static final String XMF_XMLNS = "http://www.cisco.com/schema/cisco_xmf/v1_0";

    String app_listen_addr;    
    String app_listen_port = "80";    
    String app_listen_path = "/forking";    

    ConcurrentHashMap<String, GatewayCall> callmap = new ConcurrentHashMap<>();         
    ConcurrentHashMap<String, GatewayXmf> gwmap = new ConcurrentHashMap<>();         


    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String initp;
        
        if ((initp = getInitParameter("ListenPort")) != null) app_listen_port = initp;
        if ((initp = getInitParameter("ListenPath")) != null) app_listen_path = initp;
        app_listen_addr = getInitParameter("ListenAddress");
        if (app_listen_addr == null) {
            try {app_listen_addr = InetAddress.getLocalHost().getHostAddress();} catch (UnknownHostException ex) { }
        }
        
// ***** WORK TO BE DONE =======================================================
// Handle session reconnection after break or initial failure to connect
// Add debug setting and configurable logging
// Transaction ID needs to be synchronised ???
// =============================================================================

        String appurl = "http://" + app_listen_addr + ":" + app_listen_port + config.getServletContext().getContextPath() + app_listen_path;

        Stream.of(getInitParameter("GatewayHostList").split("\\s*,\\s*")).forEach((gwhost) -> {
            try {
                String gwip = InetAddress.getByName(gwhost).getHostAddress();
                GatewayXmf gw = new GatewayXmf(gwip, appurl);
                gwmap.put(gwip, gw);
                gw.register();

            } catch (GatewayXmfException | SOAPException | UnknownHostException ex) {
                System.out.println("Error creating gateway " + gwhost + ": " + ex.getMessage());
            }
        });
    }
    
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        
        String[] pathitems;

        if (req.getPathInfo() == null || (pathitems = req.getPathInfo().split("/")).length < 2) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid URL path, missing mandatory fields");                

        } else {    
            String callid = pathitems[1];            
            GatewayCall gwcall = callmap.get("GUID:" + callid);
            
            if (gwcall == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Call with ID " + callid + " does not exist");                
                
            } else if (!gwmap.containsKey(gwcall.gwaddr)) {                
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway for call ID " + callid + " does not exist");

            } else {
                
// Process transcription or media forking control command

                String path = req.getServletPath();
                System.out.println("\nRequest (" + path + ") for GUID " + callid + "\n");

                BufferedReader streamReader = new BufferedReader(new InputStreamReader(req.getInputStream()));
                StringBuilder reqcontent = new StringBuilder();
                String inputStr;

                while ((inputStr = streamReader.readLine()) != null) {
                    reqcontent.append(inputStr);
                }

                try {
                    JSONObject reqbody = new JSONObject(reqcontent.toString());
                    System.out.println(reqbody.toString(4));

                    resp.setStatus(HttpServletResponse.SC_ACCEPTED);
                    resp.setContentType("application/json");
                    JSONObject rspbody = null;

                    switch (path) {

                        case "/forking":
                            doForking(gwcall, reqbody);
                            break;

                        case "/transcription":
                            rspbody = doTranscription(gwcall, reqbody);
                            break;
                    }
                    
                    try (PrintWriter out = resp.getWriter()) {
                        if (rspbody != null) {
                            rspbody.write(out);

                        } else {
                            out.println("{}");
                        }
                    }

                } catch (JSONException ex) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request format: " + ex.getMessage());
                    System.out.println("Invalid request JSON payload: " + ex.getMessage());
                    System.out.println(reqcontent.toString());

                } catch (MediaForkingException ex) {
                    String httperr = ex.getMessage();
                    if (ex.getCause() != null) httperr += ", caused by " + ex.getCause().getMessage();
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, httperr);
                }
            }
        }                    
    }


    private void doForking(GatewayCall gwcall, JSONObject forkreq) throws MediaForkingException {

        String action = forkreq.getString("action");
        GatewayXmf gw = gwmap.get(gwcall.gwaddr);

        switch(action.toUpperCase()) {
            case "START":
                JSONObject cg = forkreq.getJSONObject("calling");
                JSONObject cd = forkreq.getJSONObject("called");
                
                gw.startForking(gwcall.callid, cg.getString("address"), cg.getString("port"),
                                               cd.getString("address"), cd.getString("port"));
                break;

            case "STOP":
                gw.stopForking(gwcall.callid);
                break;

            default:
                throw new MediaForkingException("Invalid media forking action /" + action + "/");
        }
    }

    
    private JSONObject doTranscription(GatewayCall gwcall, JSONObject transcribereq) throws IOException, MediaForkingException {

        GatewayXmf gw = gwmap.get(gwcall.gwaddr);
        GoogleTranscriber xbr = gwcall.transcriber;

        if (xbr == null) {
            xbr = gwcall.transcriber = new GoogleTranscriber(app_listen_addr, transcribereq.optString("language", null));
        }

        String party = transcribereq.optString("party", "calling").toUpperCase();
        MediaListener stream = "CALLING".equals(party) ? xbr.cgrtp :
                               "CALLED".equals(party) ? xbr.cdrtp : xbr.cgrtp;

        gw.startForking(gwcall.callid, app_listen_addr, Integer.toString(xbr.cgrtp.getPort()), app_listen_addr, Integer.toString(xbr.cdrtp.getPort()));
        JSONObject results = xbr.transcribe(stream);
        gw.stopForking(gwcall.callid);
        
        return results;
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (req.getContentLength() > 0) {

            try (ServletOutputStream out = resp.getOutputStream()) {

// Extract the GSAPI notification body content
                
                String gwip = req.getRemoteAddr();
                SOAPMessage msg = gwmap.get(gwip).msgfct.createMessage(null, req.getInputStream());
                System.out.println("\n--- Received message from " + gwip + " ---\n");
                msg.writeTo(System.out);                
                System.out.println();

                SOAPBody body = msg.getSOAPBody();
                Document xmfmsg = body.extractContentAsDocument();
                Element msgelem = xmfmsg.getDocumentElement();
                String msgtype = msgelem.getLocalName();
                System.out.println("\n--- Message type is " + msgtype + " ---\n");
                
// Detect message type, handle call and media notifications, handle probes and respond to keep-alive the connection

                JAXBContext jaxbCtx = JAXBContext.newInstance("com.cisco.schema.cisco_xmf.v1_0");
                Unmarshaller jaxbUnmar = jaxbCtx.createUnmarshaller();

                switch (msgtype) {
                    case "SolicitXmfProbing":
                        xmfmsg.renameNode(msgelem, XMF_XMLNS, "ResponseXmfProbing");

                        for (Node next, n = msgelem.getFirstChild(); n != null; n = next) {
                            next = n.getNextSibling();

                            switch (n.getLocalName()) {
                                case "msgHeader":
                                case "sequence":
                                    break;
                                    
                                default:
                                    msgelem.removeChild(n);
                                    break;
                            }
                        }
                        break;

                    case "SolicitXmfProviderUnRegister":
                        xmfmsg.renameNode(msgelem, XMF_XMLNS, "ResponseXmfProviderUnRegister");
                        break;

                    case "NotifyXmfProviderStatus":
                        xmfmsg = null;
                        break;

                    case "NotifyXmfCallData":
                        NotifyXmfCallData cd = jaxbUnmar.unmarshal(xmfmsg, NotifyXmfCallData.class).getValue();
                        System.out.println("Call ID: " + cd.getCallData().getCallID());
                        System.out.println("Forking State: " + cd.getMediaEvent().getMediaForking().getMediaForkingState());
                        xmfmsg = null;
                        break;

                    case "NotifyXmfConnectionData":
                        NotifyXmfConnectionData cn = jaxbUnmar.unmarshal(xmfmsg, NotifyXmfConnectionData.class).getValue();

                        String callid = cn.getCallData().getCallID();
                        String connid = cn.getConnData().getConnID();
                        String callstate = cn.getConnData().getState();

                        switch (callstate) {
                            case "CONNECTED":
                                ConnDetailData detail = cn.getEvent().getConnected().getConnDetailData();
                                String guid = detail.getGuid().replaceAll("-?0x", "");
                                String direction = detail.getConnDirectionType();
                                String calling = detail.getCallingAddrData().getAddr();
                                String called = detail.getCalledAddrData().getAddr();

                                System.out.printf("Call %s, direction %s, from %s to %s, ID %s, leg %s, GUID %s%n", 
                                        callstate, direction, calling, called, callid, connid, guid);

                                if ("OUTGOING".equals(direction)) {
                                    GatewayCall gwcall = callmap.get("CALL:" + callid);
                                    if (gwcall == null) {
                                        gwcall = new GatewayCall(gwip, callid, guid);
                                        callmap.put("CALL:" + callid, gwcall);
                                        callmap.put("GUID:" + guid, gwcall);
                                    }

                                    gwcall.direction = direction;
                                    gwcall.state = callstate;
                                    gwcall.outleg = connid;
                                    gwcall.calling = calling;
                                    gwcall.called = called;
                                }

                                break;

                            case "DISCONNECTED":
                                System.out.printf("Call %s, ID %s, leg %s%n", callstate, callid, connid);
                                GatewayCall gwcall = callmap.remove("CALL:" + callid);
                                if (gwcall != null) callmap.remove("GUID:" + gwcall.guid);
                                break;                                                                
                                
                            default:
                                break;
                        }

// Add debug setting later to turn on/off or retrieve calls via web request

                        if (false) {
                            callmap.forEach((k, c) -> {
                                if (k.startsWith("CALL:")) {
                                    System.out.printf("%s, ID %s, GUID %s, leg %s, from %s to %s%n", 
                                            c.direction, c.callid, c.guid, c.outleg, c.calling, c.called);
                                }
                            });
                        }
                        
                        xmfmsg = null;
                        break;
                }

                if (xmfmsg != null) {
                    body.addDocument(xmfmsg);
                    resp.setContentType("application/soap+xml");
                    msg.writeTo(out);

                    System.out.println("--- Sent XMF response ---\n");
                    msg.writeTo(System.out);
                    System.out.println();
                }

            } catch (JAXBException | SOAPException ex) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            }
        }
    }

    
// <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
        public String getServletInfo() {
        return "Provides simple web interface onto recording controls";
    }// </editor-fold>

}
