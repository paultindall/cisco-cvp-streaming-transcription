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
 * MEDIA LISTENING AND HANDLER
 * 
 * Handles asynchronous reading of forked RTP streams.  Currently assumes G.711 and
 * 20ms packetisation.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MediaListener {

    static int RTPBASEPORT = 16384;
    static int RTPBUFLEN = 172;
    private static int port = 0;

    private final DatagramChannel chn;
    private final ByteBuffer rxbuf;
    private final int rxport;
    private boolean active;
    private Consumer<byte[]> pkthandler;


    public MediaListener(String addr) throws IOException {

        chn = DatagramChannel.open();
        rxport = (RTPBASEPORT + port++) & 0xffff;
        chn.socket().bind(new InetSocketAddress(addr, rxport));
        rxbuf = ByteBuffer.allocate(RTPBUFLEN);
    }


    public void start() {
        if (!active) {
            readAsync();
            active = true;
        }
    }


    public void stop() {
        active = false;
    }


    public int getPort() {
        return rxport;
    }


    public void processMedia(Consumer<byte[]> handler) {
        pkthandler = handler;
    }


    public void discardMedia() {
        pkthandler = null;
    }


    private void readAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                SocketAddress client = chn.receive(rxbuf);
//                System.out.format("Received %d bytes on port %d from %s%n",  rxbuf.position(), rxport, client);
            } catch (IOException ex) {
                System.out.println(ex);
            }

        }).thenRunAsync(() -> {
            if (pkthandler != null) {
                int pktlen = rxbuf.position();
                byte[] hdr = Arrays.copyOfRange(rxbuf.array(), 0, 12);
                byte[] payload = Arrays.copyOfRange(rxbuf.array(), 12, pktlen);
                long seq = (((int) hdr[2] & 0xff) << 8) + (((int) hdr[3]) & 0xff);
//                System.out.format("Processing RTP packet on port %d, bytes = %d, sequence = %d%n", rxport, pktlen, seq);
                pkthandler.accept(payload);
            }
            rxbuf.clear();
            if (active) readAsync();
        });
    }
}
