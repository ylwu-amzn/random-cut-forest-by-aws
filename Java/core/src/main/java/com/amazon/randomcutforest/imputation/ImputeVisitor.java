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

package com.amazon.randomcutforest.imputation;

import com.amazon.randomcutforest.CommonUtils;
import com.amazon.randomcutforest.MultiVisitor;
import com.amazon.randomcutforest.anomalydetection.AnomalyScoreVisitor;
import com.amazon.randomcutforest.tree.INodeView;

import java.util.Arrays;

import static com.amazon.randomcutforest.CommonUtils.checkArgument;
import static com.amazon.randomcutforest.CommonUtils.toDoubleArray;

/**
 * A MultiVisitor which imputes missing values in a point. The missing values
 * are first imputed with the corresponding values in the leaf node in the
 * traversal path. Then, when this MultiVisitor is merged with another
 * MultiVisitor, we keep the imputed values with a lower rank, where the rank
 * value is the anomaly score for the imputed point.
 */
public class ImputeVisitor implements MultiVisitor<double[]> {

    // default large values for initialization; consider -ve log( 0 )
    public static double DEFAULT_INIT_VALUE = Double.MAX_VALUE / 4;

    /**
     * an array that helps indicate the missing entires in the tree space
     */
    protected final boolean[] missing;

    /**
     * an array that helps indicate the missing entires in the forest space
     */
    protected final boolean[] liftedMissing;

    /**
     * the query point in the tree space, where the missing entries (in tree space)
     * would be overwritten
     */
    protected float[] queryPoint;

    /**
     * the query point in the forest space, where the missing entries (in forest
     * space) would be overwritten
     */
    protected float[] liftedPoint;

    /**
     * the unnormalized anomaly score of a point, should be interpreted as -ve
     * log(likelihood)
     */
    protected double anomalyRank;

    /**
     * distance of the point in the forest space, this is not tree specific
     */
    protected double distance;

    /**
     * a parameter that controls central estimation ( = 1.0) and fully random sample
     * over entire range ( = 0.0 )
     */
    protected double centrality;

    protected boolean converged;

    /**
     * Create a new ImputeVisitor.
     *
     * @param liftedPoint          The point with missing values we want to impute
     * @param queryPoint           The projected point in the tree space
     * @param liftedMissingIndexes the original missing indices
     * @param missingIndexes       The indexes of the missing values in the tree
     *                             space
     */
    public ImputeVisitor(float[] liftedPoint, float[] queryPoint, int[] liftedMissingIndexes, int[] missingIndexes,
            double centrality) {
        this.liftedPoint = Arrays.copyOf(liftedPoint, liftedPoint.length);
        this.queryPoint = Arrays.copyOf(queryPoint, queryPoint.length);
        this.missing = new boolean[queryPoint.length];
        this.liftedMissing = new boolean[liftedPoint.length];
        this.centrality = centrality;

        if (missingIndexes == null) {
            missingIndexes = new int[0];
        }

        for (int i = 0; i < missingIndexes.length; i++) {
            checkArgument(0 <= missingIndexes[i] && missingIndexes[i] < queryPoint.length,
                    "Missing value indexes must be between 0 (inclusive) and queryPoint.length (exclusive)");

            missing[missingIndexes[i]] = true;
        }

        for (int i = 0; i < liftedMissingIndexes.length; i++) {
            checkArgument(0 <= liftedMissingIndexes[i] && liftedMissingIndexes[i] < liftedPoint.length,
                    "Missing value indexes must be between 0 (inclusive) and liftedPoint.length (exclusive)");

            liftedMissing[liftedMissingIndexes[i]] = true;
        }

        anomalyRank = DEFAULT_INIT_VALUE;
        distance = DEFAULT_INIT_VALUE;
    }

    public ImputeVisitor(float[] queryPoint, int numberOfMissingIndices, int[] missingIndexes) {
        this(queryPoint, Arrays.copyOf(queryPoint, queryPoint.length),
                Arrays.copyOf(missingIndexes, Math.min(numberOfMissingIndices, missingIndexes.length)),
                Arrays.copyOf(missingIndexes, Math.min(numberOfMissingIndices, missingIndexes.length)), 1.0);
    }

    /**
     * A copy constructor which creates a deep copy of the original ImputeVisitor.
     *
     * @param original
     */
    ImputeVisitor(ImputeVisitor original) {
        int length = original.queryPoint.length;
        this.queryPoint = Arrays.copyOf(original.queryPoint, length);
        this.missing = Arrays.copyOf(original.missing, length);
        this.liftedPoint = Arrays.copyOf(original.liftedPoint, original.liftedPoint.length);
        this.liftedMissing = Arrays.copyOf(original.liftedMissing, original.liftedPoint.length);
        anomalyRank = DEFAULT_INIT_VALUE;
        distance = DEFAULT_INIT_VALUE;
    }

    /**
     * Update the rank value using the probability that the imputed query point is
     * separated from this bounding box in a random cut. This step is conceptually
     * the same as * {@link AnomalyScoreVisitor#accept}.
     *
     * @param node        the node being visited
     * @param depthOfNode the depth of the node being visited
     */
    public void accept(final INodeView node, final int depthOfNode) {

        double probabilityOfSeparation = node.probailityOfSeparation(queryPoint);

        if (probabilityOfSeparation <= 0) {
            return;
        }

        converged = (probabilityOfSeparation == 0);
        anomalyRank = probabilityOfSeparation * scoreUnseen(depthOfNode, node.getMass())
                + (1 - probabilityOfSeparation) * anomalyRank;
    }

    /**
     * Impute the missing values in the query point with the corresponding values in
     * the leaf point. Set the rank to the score function evaluated at the leaf
     * node.
     *
     * @param leafNode    the leaf node being visited
     * @param depthOfNode the depth of the leaf node
     */
    @Override
    public void acceptLeaf(final INodeView leafNode, final int depthOfNode) {
        float[] leafPoint = leafNode.getLeafPoint();
        for (int i = 0; i < queryPoint.length; i++) {
            if (missing[i]) {
                queryPoint[i] = leafPoint[i];
            }
        }
        float[] liftedLeafPoint = leafNode.getLiftedLeafPoint();
        double squaredDistance = 0;
        for (int i = 0; i < liftedLeafPoint.length; i++) {
            if (liftedMissing[i]) {
                liftedPoint[i] = liftedLeafPoint[i];
            } else {
                double t = (liftedLeafPoint[i] - liftedPoint[i]);
                squaredDistance += t * t;
            }
        }
        distance = Math.sqrt(squaredDistance);
        if (distance <= 0) {
            converged = true;
            if (depthOfNode == 0) {
                anomalyRank = 0;
            } else {
                anomalyRank = scoreSeen(depthOfNode, leafNode.getMass());
            }
        } else {
            anomalyRank = scoreUnseen(depthOfNode, leafNode.getMass());
        }
    }

    /**
     * @return the imputed point.
     */
    @Override
    public double[] getResult() {
        return toDoubleArray(liftedPoint);
    }

    /**
     * An ImputeVisitor should split whenever the cut dimension in a node
     * corresponds to a missing value in the query point.
     *
     * @param node A node in the tree traversal
     * @return true if the cut dimension in the node corresponds to a missing value
     *         in the query point, false otherwise.
     */
    @Override
    public boolean trigger(final INodeView node) {
        return missing[node.getCutDimension()];
    }

    protected double getAnomalyRank() {
        return anomalyRank;
    }

    protected double getDistance() {
        return distance;
    }

    /**
     * @return a copy of this visitor.
     */
    @Override
    public MultiVisitor<double[]> newCopy() {
        return new ImputeVisitor(this);
    }

    protected boolean updateCombine(ImputeVisitor other) {
        return other.anomalyRank < anomalyRank;
    }

    /**
     * If this visitor as a lower rank than the second visitor, do nothing.
     * Otherwise, overwrite this visitor's imputed values withe the valuse from the
     * second visitor.
     *
     * @param other A second visitor
     */
    @Override
    public void combine(MultiVisitor<double[]> other) {
        ImputeVisitor visitor = (ImputeVisitor) other;
        if (updateCombine(visitor)) {
            updateFrom(visitor);
        }
    }

    protected void updateFrom(ImputeVisitor visitor) {
        System.arraycopy(visitor.queryPoint, 0, queryPoint, 0, queryPoint.length);
        System.arraycopy(visitor.liftedPoint, 0, liftedPoint, 0, liftedPoint.length);
        anomalyRank = visitor.anomalyRank;
        converged = visitor.converged;
        distance = visitor.distance;
    }

    protected double scoreSeen(int depth, int mass) {
        return CommonUtils.defaultScoreSeenFunction(depth, mass);
    }

    protected double scoreUnseen(int depth, int mass) {
        return CommonUtils.defaultScoreUnseenFunction(depth, mass);
    }

    @Override
    public boolean isConverged() {
        return converged;
    }
}
