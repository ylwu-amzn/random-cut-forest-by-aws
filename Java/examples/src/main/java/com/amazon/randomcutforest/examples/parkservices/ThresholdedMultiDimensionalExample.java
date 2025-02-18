/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.randomcutforest.examples.parkservices;

import java.util.Arrays;
import java.util.Random;

import com.amazon.randomcutforest.config.ForestMode;
import com.amazon.randomcutforest.config.Precision;
import com.amazon.randomcutforest.examples.Example;
import com.amazon.randomcutforest.parkservices.AnomalyDescriptor;
import com.amazon.randomcutforest.parkservices.ThresholdedRandomCutForest;
import com.amazon.randomcutforest.testutils.MultiDimDataWithKey;
import com.amazon.randomcutforest.testutils.ShingledMultiDimDataWithKeys;

public class ThresholdedMultiDimensionalExample implements Example {

    public static void main(String[] args) throws Exception {
        new ThresholdedMultiDimensionalExample().run();
    }

    @Override
    public String command() {
        return "Thresholded_Multi_Dim_example";
    }

    @Override
    public String description() {
        return "Thresholded Multi Dimensional Example";
    }

    @Override
    public void run() throws Exception {
        // Create and populate a random cut forest

        int shingleSize = 4;
        int numberOfTrees = 50;
        int sampleSize = 256;
        Precision precision = Precision.FLOAT_32;
        int dataSize = 4 * sampleSize;

        // change this to try different number of attributes,
        // this parameter is not expected to be larger than 5 for this example
        int baseDimensions = 2;

        int dimensions = baseDimensions * shingleSize;
        ThresholdedRandomCutForest forest = ThresholdedRandomCutForest.builder().compact(true).dimensions(dimensions)
                .randomSeed(0).numberOfTrees(numberOfTrees).shingleSize(shingleSize).sampleSize(sampleSize)
                .precision(precision).anomalyRate(0.01).forestMode(ForestMode.STANDARD).build();

        long seed = new Random().nextLong();
        System.out.println("seed = " + seed);
        // change the last argument seed for a different run
        MultiDimDataWithKey dataWithKeys = ShingledMultiDimDataWithKeys.generateShingledDataWithKey(dataSize, 50,
                shingleSize, baseDimensions, seed);
        int keyCounter = 0;
        int count = 0;
        for (double[] point : dataWithKeys.data) {

            AnomalyDescriptor result = forest.process(point, 0L);

            if (keyCounter < dataWithKeys.changeIndices.length
                    && count + shingleSize - 1 == dataWithKeys.changeIndices[keyCounter]) {
                System.out.println("timestamp " + (count + shingleSize - 1) + " CHANGE "
                        + Arrays.toString(dataWithKeys.changes[keyCounter]));
                ++keyCounter;
            }

            if (result.getAnomalyGrade() != 0) {
                System.out.print("timestamp " + (count + shingleSize - 1) + " RESULT value ");
                for (int i = (shingleSize - 1) * baseDimensions; i < shingleSize * baseDimensions; i++) {
                    System.out.print(result.getCurrentInput()[i] + ", ");
                }
                System.out.print("score " + result.getRCFScore() + ", grade " + result.getAnomalyGrade() + ", ");

                if (result.isExpectedValuesPresent()) {
                    if (result.getRelativeIndex() != 0 && result.isStartOfAnomaly()) {
                        System.out.print(-result.getRelativeIndex() + " steps ago, instead of ");
                        for (int i = 0; i < baseDimensions; i++) {
                            System.out.print(result.getPastValues()[i] + ", ");
                        }
                        System.out.print("expected ");
                        for (int i = 0; i < baseDimensions; i++) {
                            System.out.print(result.getExpectedValuesList()[0][i] + ", ");
                            if (result.getPastValues()[i] != result.getExpectedValuesList()[0][i]) {
                                System.out.print("( "
                                        + (result.getPastValues()[i] - result.getExpectedValuesList()[0][i]) + " ) ");
                            }
                        }
                    } else {
                        System.out.print("expected ");
                        for (int i = 0; i < baseDimensions; i++) {
                            System.out.print(result.getExpectedValuesList()[0][i] + ", ");
                            if (result.getCurrentInput()[(shingleSize - 1) * baseDimensions
                                    + i] != result.getExpectedValuesList()[0][i]) {
                                System.out
                                        .print("( " + (result.getCurrentInput()[(shingleSize - 1) * baseDimensions + i]
                                                - result.getExpectedValuesList()[0][i]) + " ) ");
                            }
                        }
                    }
                }
                System.out.println();
            }
            ++count;
        }

    }

}
