package com.amazon.randomcutforest.examples.serialization;

import com.amazon.randomcutforest.parkservices.ThresholdedRandomCutForest;
import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestMapper;
import com.google.gson.Gson;

import java.util.Random;

public class GsonExample {

    static Gson gson = new Gson();
    static ThresholdedRandomCutForestMapper trcfMapper = new ThresholdedRandomCutForestMapper();;

    public static void main(String[] args) throws Exception {

        String gsonString = teststate();
        ThresholdedRandomCutForest forest = gson.fromJson(gsonString, ThresholdedRandomCutForest.class);
        System.out.println("Parsed tree component size " + forest.getForest().getComponents().size());
    }

    public static String teststate() {
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

        String gsonString = gson.toJson(trcfMapper.toState(trcf));
        System.out.println(gsonString);
        return gsonString;
    }
}
