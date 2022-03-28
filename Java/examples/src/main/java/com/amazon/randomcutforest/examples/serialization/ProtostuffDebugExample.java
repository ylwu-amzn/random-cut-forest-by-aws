package com.amazon.randomcutforest.examples.serialization;

import com.amazon.randomcutforest.parkservices.ThresholdedRandomCutForest;
import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestMapper;
import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestState;
import com.google.gson.Gson;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

public class ProtostuffDebugExample {
    static Gson gson = new Gson();
    static ThresholdedRandomCutForestMapper trcfMapper = new ThresholdedRandomCutForestMapper();;
    static Schema<ThresholdedRandomCutForestState> trcfSchema = AccessController
            .doPrivileged(
                    (PrivilegedAction<Schema<ThresholdedRandomCutForestState>>) () -> RuntimeSchema
                            .getSchema(ThresholdedRandomCutForestState.class)
            );

    public static void main(String[] args) throws Exception {
        //testParseTRCF();
        testParseADModel();
    }

    public static void testParseTRCF() {
        ThresholdedRandomCutForest trcf = teststate();
        LinkedBuffer buffer = LinkedBuffer.allocate(1024);
        String checkpoint = toCheckpoint(trcf, buffer);
        System.out.println(checkpoint);
        ThresholdedRandomCutForest forest = toTrcf(checkpoint);
        System.out.println("Forest component size " + forest.getForest().getComponents().size()); // 30
    }

    public static void testParseADModel() throws IOException {
        String filePath = "/Users/ylwu/code/os/random-cut-forest-by-aws/Java/examples/src/main/resources/1_3_0_rcf_model_not_null.json";
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        Map map = gson.fromJson(json, Map.class);
        String model = (String)((Map)((Map)((ArrayList)((Map)map.get("hits")).get("hits")).get(0)).get("_source")).get("modelV2");
        System.out.println(model);
        ThresholdedRandomCutForest forest = toTrcf(json);
        System.out.println(forest);
    }


    private static String toCheckpoint(ThresholdedRandomCutForest trcf, LinkedBuffer buffer) {
        try {
            byte[] bytes = AccessController.doPrivileged((PrivilegedAction<byte[]>) () -> {
                ThresholdedRandomCutForestState trcfState = trcfMapper.toState(trcf);
                return ProtostuffIOUtil.toByteArray(trcfState, trcfSchema, buffer);
            });
            return Base64.getEncoder().encodeToString(bytes);
        } finally {
            buffer.clear();
        }
    }

    private static ThresholdedRandomCutForest teststate() {
        Random r = new Random();
        double rangeMin = 100;
        double rangeMax = 200;
        ThresholdedRandomCutForest trcf = new ThresholdedRandomCutForest(
                ThresholdedRandomCutForest
                        .builder()
                        .dimensions(1)
                        .sampleSize(256)
                        .numberOfTrees(30)
                        .timeDecay(0.0001)
                        .outputAfter(32)
                        .initialAcceptFraction(0.125d)
                        .parallelExecutionEnabled(false)
                        .internalShinglingEnabled(true)
                        .anomalyRate(1 - 0.995)
        );
        long timestamp = 1648255374000l;
        for (int i = 0; i < 10000; i++) {
            double randomValue = rangeMin + (rangeMax - rangeMin) * r.nextDouble();
            trcf.process(new double[]{randomValue}, timestamp + i * 60_000);
        }

        //String gsonString = gson.toJson(trcfMapper.toState(trcf));
        return trcf;
    }


    private static ThresholdedRandomCutForest toTrcf(String checkpoint) {
        ThresholdedRandomCutForest trcf = null;


        if (checkpoint != null && !checkpoint.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(checkpoint);
                ThresholdedRandomCutForestState state = trcfSchema.newMessage();
                AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                    ProtostuffIOUtil.mergeFrom(bytes, state, trcfSchema);
                    return null;
                });
                String stateString = gson.toJson(state);
                //System.out.println(stateString);
                trcf = trcfMapper.toModel(state);
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            }
        }
        return trcf;
    }
}
