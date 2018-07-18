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
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse;
import static com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse.SpeechEventType.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import org.json.JSONObject;


public class GoogleTranscriber {
    MediaListener cgrtp;
    MediaListener cdrtp;
    RecognitionConfig reccfg;
    StreamingRecognitionConfig strcfg;
        
    public GoogleTranscriber(String addr) throws IOException {
        cgrtp = new MediaListener(addr);
        cdrtp = new MediaListener(addr);

        reccfg = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.MULAW)
                    .setSampleRateHertz(8000)
                    .setLanguageCode("en-US")
                    .build();

        strcfg = StreamingRecognitionConfig.newBuilder()
                    .setConfig(reccfg)
                    .setSingleUtterance(true)
                    .build();
    }


    public JSONObject transcribeCaller() throws IOException {

        JSONObject outcome = new JSONObject();
        
        try (SpeechClient speech = SpeechClient.create()) {
            BidiStream<StreamingRecognizeRequest, StreamingRecognizeResponse> stream = speech.streamingRecognizeCallable().call();
            StreamingRecognizeRequest cfgreq = StreamingRecognizeRequest.newBuilder().setStreamingConfig(strcfg).build();
            stream.send(cfgreq);

            cgrtp.start();
            cgrtp.processMedia((raw) -> {
                stream.send(StreamingRecognizeRequest.newBuilder().setAudioContent(ByteString.copyFrom(raw)).build());
            });

            for (StreamingRecognizeResponse rsp : stream) {

                System.out.println("====================================================");
                System.out.println("Error: " + rsp.getError().getCode() + " " + rsp.getError().getMessage());
                System.out.println("Event: " + rsp.getSpeechEventType().toString());
                rsp.getResultsList().iterator().forEachRemaining(action -> {
                    System.out.println("Final: " + action.getIsFinal());
                    action.getAlternativesList().iterator().forEachRemaining(tr -> {
                        System.out.println("Transcript: " + tr.getTranscript());
                    });
                });
                System.out.println("----------------------------------------------------");

                if (rsp.getSpeechEventType().equals(END_OF_SINGLE_UTTERANCE)) {
                    cgrtp.discardMedia();
                    stream.closeSend();

                } else if (rsp.getError().getCode() != 0) {
                    outcome.put("error", rsp.getError().getMessage());
                    stream.cancel();                    

                } else {
                    StreamingRecognitionResult result = rsp.getResultsList().get(0);
                    if (result.getIsFinal()) {
                        outcome.put("transcript", result.getAlternatives(0).getTranscript());
                        stream.cancel();
                    }
                }
            }

        } finally {
            cgrtp.discardMedia();
            cgrtp.stop();
        }

        return outcome;
    }
}
