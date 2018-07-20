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
 * GATEWAY GSAPI COMMANDS
 * 
 * Handles XMF provider commands to the gateway.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco,  6 Jul 2018 Initial version for PoC
 * -----------------------------------------------------------------------------------
 */

import com.cisco.schema.cisco_xmf.v1_0.Action;
import com.cisco.schema.cisco_xmf.v1_0.ApplicationData;
import com.cisco.schema.cisco_xmf.v1_0.EnableMediaForking;
import com.cisco.schema.cisco_xmf.v1_0.FarEndAddr;
import com.cisco.schema.cisco_xmf.v1_0.MsgHeader;
import com.cisco.schema.cisco_xmf.v1_0.NearEndAddr;
import com.cisco.schema.cisco_xmf.v1_0.ProviderData;
import com.cisco.schema.cisco_xmf.v1_0.RequestXmfCallMediaForking;
import com.cisco.schema.cisco_xmf.v1_0.RequestXmfRegister;
import com.cisco.schema.cisco_xmf.v1_0.ResponseXmfRegister;
import java.io.IOException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;


public class GatewayXmf {

    String CONNECTION_EVENTS = "CONNECTED DISCONNECTED";
    String MEDIA_EVENTS = "MEDIA_ACTIVITY";
    String APP_NAME = "com.cisco.pt.cvp.forking";
    String GW_XMF_URL = "http://%s:8090/cisco_xmf";

    String iphost;
    String appurl;
    String xmfurl;
    String regid;
    int transaction;
    boolean active;

    MessageFactory msgfct;


    public GatewayXmf(String iphost, String appurl) throws SOAPException {
        this.iphost = iphost;
        this.appurl = appurl;
        
        msgfct = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
    }
    

    public void register() throws GatewayXmfException {

        xmfurl = String.format(GW_XMF_URL, iphost);

        try {
            RequestXmfRegister reg = new RequestXmfRegister();
            MsgHeader msghdr = new MsgHeader();
            ProviderData prvdata = new ProviderData();
            ApplicationData appdata = new ApplicationData();

            msghdr.setTransactionID(String.valueOf(++transaction));
            prvdata.setUrl(xmfurl);
            appdata.setName(APP_NAME);
            appdata.setUrl(appurl);

            reg.setConnectionEventsFilter(CONNECTION_EVENTS);
            reg.setMediaEventsFilter(MEDIA_EVENTS);
            reg.setApplicationData(appdata);
            reg.setProviderData(prvdata);
            reg.setMsgHeader(msghdr);

            SOAPMessage rsp = sendRequest(reg);

            JAXBContext jaxbCtx = JAXBContext.newInstance("com.cisco.schema.cisco_xmf.v1_0");
            Unmarshaller jaxbUnmar = jaxbCtx.createUnmarshaller();
            ResponseXmfRegister rspreg = jaxbUnmar.unmarshal(rsp.getSOAPBody().extractContentAsDocument(), ResponseXmfRegister.class).getValue();
            regid = rspreg.getMsgHeader().getRegistrationID();
            System.out.println("Gateway connection successful to " + iphost + ", registration ID = " + regid);
            active = true;

        } catch (JAXBException | SOAPException | UnsupportedOperationException ex ) {
            throw new GatewayXmfException("XMF register error", ex);
        }        
    }


    public void startForking(String gwcallid, String cgaddr, String cgport, String cdaddr, String cdport) throws MediaForkingException {
        
        try {
            RequestXmfCallMediaForking fork = new RequestXmfCallMediaForking();
            
            NearEndAddr near = new NearEndAddr();
            near.setIpv4(cgaddr);
            near.setPort(cgport);                        

            FarEndAddr far = new FarEndAddr();
            far.setIpv4(cdaddr);
            far.setPort(cdport);                        

            EnableMediaForking enable = new EnableMediaForking();
            enable.setNearEndAddr(near);
            enable.setFarEndAddr(far);

            Action act = new Action();
            act.setEnableMediaForking(enable);
            
            MsgHeader msghdr = new MsgHeader();
            msghdr.setRegistrationID(regid);
            msghdr.setTransactionID(String.valueOf(++transaction));
            
            fork.setCallID(gwcallid);
            fork.setMsgHeader(msghdr);
            fork.setAction(act);
                    
            sendRequest(fork);

        } catch (GatewayXmfException ex) {
            throw new MediaForkingException("Error starting media forking", ex);
        }
        
        
    }


    public void stopForking(String gwcallid) throws MediaForkingException {
        
        try {
            RequestXmfCallMediaForking fork = new RequestXmfCallMediaForking();
            
            Action act = new Action();
            act.setDisableMediaForking("");
            
            MsgHeader msghdr = new MsgHeader();
            msghdr.setRegistrationID(regid);
            msghdr.setTransactionID(String.valueOf(++transaction));
            
            fork.setCallID(gwcallid);
            fork.setMsgHeader(msghdr);
            fork.setAction(act);
                    
            sendRequest(fork);

        } catch (GatewayXmfException ex) {
            throw new MediaForkingException("Error stopping media forking", ex);
        }
    }


    SOAPMessage sendRequest(Object jaxbe) throws GatewayXmfException {

        SOAPConnection con = null;
        SOAPMessage rsp = null;

        try {
            JAXBContext jaxbCtx = JAXBContext.newInstance("com.cisco.schema.cisco_xmf.v1_0");
            Marshaller jaxbMar = jaxbCtx.createMarshaller();

            SOAPMessage msg = msgfct.createMessage();
            SOAPEnvelope env = msg.getSOAPPart().getEnvelope();
            SOAPBody body = env.getBody();
            jaxbMar.marshal(jaxbe, body);

            System.out.println("\n--- " + jaxbe.getClass().getSimpleName() + " to gateway " + iphost + " ---\n");
            msg.writeTo(System.out);
            System.out.println();

            con = SOAPConnectionFactory.newInstance().createConnection();
            rsp = con.call(msg, xmfurl);
            con.close();

            System.out.println("\n--- Gateway XMF response ---\n");
            rsp.writeTo(System.out);
            System.out.println();
            
            SOAPFault fault = rsp.getSOAPBody().getFault();
            if (fault != null) {
                throw new GatewayXmfException(fault);
            }

        } catch (IOException | JAXBException | SOAPException ex) {
            if (con != null) {try { con.close(); } catch (SOAPException e) { } }
            throw new GatewayXmfException("XMF request error", ex);
        }
        
        return rsp;
    }
}
