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
 * MEDIA FORKING CONTROL EXCEPTION
 * 
 * Basic custom exception for errors during media fork stream handling.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

public class MediaForkingException extends Exception {

    public MediaForkingException() {
    }

    public MediaForkingException(String message) {
        super(message);
    }

    public MediaForkingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MediaForkingException(Throwable cause) {
        super(cause);
    }    
}
