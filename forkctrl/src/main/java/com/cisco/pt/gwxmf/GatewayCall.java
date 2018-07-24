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
 * GATEWAY CALL STATUS
 * 
 * Holds mapping of GUID and call leg ID to the gateway call ID that is needed for
 * media forking control operations.  Also holds call status for active calls and
 * can readily be extended for other use cases based on GSAPI.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco,  4 Jun 2018 Initial version, for PoC, not hardened
 * -----------------------------------------------------------------------------------
 */

public class GatewayCall {
    String gwaddr;
    String callid;
    String guid;
    String outleg;
    String state;
    String direction;
    String calling;
    String called;
    GoogleTranscriber transcriber;

    public GatewayCall(String callid) {
        this.callid = callid;
    }

    public GatewayCall(String callid, String guid) {
        this.callid = callid;
        this.guid = guid;
    }

    public GatewayCall(String gwaddr, String callid, String guid) {
        this.gwaddr = gwaddr;
        this.callid = callid;
        this.guid = guid;
    }
}
