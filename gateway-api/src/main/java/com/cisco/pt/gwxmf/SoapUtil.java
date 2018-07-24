package com.cisco.pt.gwxmf;

import org.slf4j.Logger;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class SoapUtil {
    public static String writeToString(SOAPMessage message) throws IOException, SOAPException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString("UTF-8");
    }

    public static void writeToDebugLog(Logger logger, SOAPMessage message)
            throws IOException, SOAPException {
        if (logger.isDebugEnabled()) {
            logger.debug(writeToString(message));
        }
    }
}
