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
 * SPEECH TO TEXT USING GOOGLE CLOUD
 * 
 * Handles streaming of media for recognition/transcription using Google Speech Service.
 *
 * -----------------------------------------------------------------------------------
 * 1.0,  Paul Tindall, Cisco, 13 Jul 2018 Initial version
 * -----------------------------------------------------------------------------------
 */

import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.speech.v1p1beta1.*;
import com.google.protobuf.ByteString;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.stream.Collectors;

import static com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse.SpeechEventType.END_OF_SINGLE_UTTERANCE;


public class GoogleTranscriber {
    private static final Logger logger = LoggerFactory.getLogger(GoogleTranscriber.class);

    private final String DEFAULT_LANGUAGE = "en-US";
    MediaListener cgrtp;
    MediaListener cdrtp;
    RecognitionConfig reccfg;
    StreamingRecognitionConfig strcfg;
        

    public GoogleTranscriber(String addr) throws IOException {
        this(addr, null);
    }


    public GoogleTranscriber(String addr, String lang) throws IOException {
        cgrtp = new MediaListener(addr);
        cdrtp = new MediaListener(addr);

        if (lang == null) {
            lang = DEFAULT_LANGUAGE;
        }
        
        reccfg = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.MULAW)
                    .setSampleRateHertz(8000)
                    .setLanguageCode(lang)
                    .build();

        strcfg = StreamingRecognitionConfig.newBuilder()
                    .setConfig(reccfg)
                    .setSingleUtterance(true)
                    .build();
    }


    public JSONObject transcribeCaller() throws IOException {
        return transcribe(cgrtp);
    }


    public JSONObject transcribeCalled() throws IOException {
        return transcribe(cdrtp);
    }


    public JSONObject transcribe(MediaListener rtp) throws IOException {

        JSONObject outcome = new JSONObject();
        
        try (SpeechClient speech = SpeechClient.create()) {
            BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream = speech.streamingRecognizeCallable().call();
            StreamingRecognizeRequest cfgreq = StreamingRecognizeRequest.newBuilder().setStreamingConfig(strcfg).build();
            stream.send(cfgreq);

            rtp.start();
            rtp.processMedia((raw) -> {
                stream.send(StreamingRecognizeRequest.newBuilder().setAudioContent(ByteString.copyFrom(raw)).build());
            });

            for (StreamingRecognizeResponse rsp : stream) {
                if (logger.isDebugEnabled()) {
                    String results = rsp.getResultsList().stream().map((result) -> {
                        String transcripts = result.getAlternativesList().stream()
                                .map((alt) -> String.format("Transcript: %s\nConfidence: %f",
                                        alt.getTranscript(),
                                        alt.getConfidence()))
                                .collect(Collectors.joining("\n"));
                        return String.format("Final: %b\n%s", result.getIsFinal(), transcripts);
                    }).collect(Collectors.joining("\n"));
                    logger.debug("====================================================\n" +
                                    "Error: {} {}\n" +
                                    "Event: {}\n" +
                                    "{}\n" +
                                    "----------------------------------------------------",
                            rsp.getError().getCode(),
                            rsp.getError().getMessage(),
                            rsp.getSpeechEventType(),
                            results);
                }

                if (rsp.getSpeechEventType().equals(END_OF_SINGLE_UTTERANCE)) {
                    rtp.discardMedia();
                    stream.closeSend();

                } else if (rsp.getError().getCode() != 0) {
                    outcome.put("error", rsp.getError().getMessage());
                    stream.cancel();                    

                } else {
                    StreamingRecognitionResult result = rsp.getResultsList().get(0);
                    if (result.getIsFinal()) {
                        SpeechRecognitionAlternative alt = result.getAlternatives(0);
                        outcome.put("transcript", alt.getTranscript())
                               .put("confidence", (new DecimalFormat("0.00")).format(alt.getConfidence()));
                        stream.cancel();
                    }
                }

            }

        } finally {
            rtp.discardMedia();
            rtp.stop();
        }

        return outcome;
    }
}
