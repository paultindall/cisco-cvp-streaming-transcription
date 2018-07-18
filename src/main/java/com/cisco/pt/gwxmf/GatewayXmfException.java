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
 * GSAPI XMF COMMUNICATION EXCEPTION
 * 
 * Handles detailed failure information returned as SOAP fault.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

import java.util.Iterator;
import javax.xml.soap.Detail;
import javax.xml.soap.DetailEntry;
import javax.xml.soap.SOAPFault;
import org.w3c.dom.Element;

public class GatewayXmfException extends Exception {

    private String reason;
    private String detailText;
    private String detailOperation;

    public GatewayXmfException () {
    }

    public GatewayXmfException (String message) {
        super(message);
    }

    public GatewayXmfException (SOAPFault fault) {
        super("XMF operation failed");
        reason = fault.getFaultString();
        getFaultDetails(fault.getDetail());        
    }

    public GatewayXmfException (Throwable cause) {
        super(cause);
    }

    public GatewayXmfException (String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        if (reason != null) msg += ": " + reason;
        if (detailOperation != null) msg += " on " + detailOperation;
        if (detailText != null) msg += " (" + detailText + ")";
        return msg;
    }
    
    private void getFaultDetails(Detail faultDet) {
        Iterator<Element> children = ((DetailEntry) faultDet.getDetailEntries().next()).getChildElements();

        children.forEachRemaining((e) -> {
            switch (e.getNodeName()) {
                case "text":
                    detailText = e.getTextContent();
                    break;
                    
                case "operation":
                    detailOperation = e.getTextContent();
                    break;
            }
        });        
    }
}
