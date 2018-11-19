/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.evaluation;

import org.junit.Test;
import org.nd4j.evaluation.regression.RegressionEvaluation;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.nd4j.linalg.indexing.NDArrayIndex.all;
import static org.nd4j.linalg.indexing.NDArrayIndex.interval;

/**
 * @author Alex Black
 */
public class RegressionEvalTest  extends BaseNd4jTest {

    public RegressionEvalTest(Nd4jBackend backend) {
        super(backend);
    }

    @Override
    public char ordering() {
        return 'c';
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEvalParameters() {
        int specCols = 5;
        INDArray labels = Nd4j.ones(3);
        INDArray preds = Nd4j.ones(6);
        RegressionEvaluation eval = new RegressionEvaluation(specCols);

        eval.eval(labels, preds);
    }

    @Test
    public void testPerfectPredictions() {

        int nCols = 5;
        int nTestArrays = 100;
        int valuesPerTestArray = 3;
        RegressionEvaluation eval = new RegressionEvaluation(nCols);

        for (int i = 0; i < nTestArrays; i++) {
            INDArray rand = Nd4j.rand(valuesPerTestArray, nCols);
            eval.eval(rand, rand);
        }

        System.out.println(eval.stats());

        for (int i = 0; i < nCols; i++) {
            assertEquals(0.0, eval.meanSquaredError(i), 1e-6);
            assertEquals(0.0, eval.meanAbsoluteError(i), 1e-6);
            assertEquals(0.0, eval.rootMeanSquaredError(i), 1e-6);
            assertEquals(0.0, eval.relativeSquaredError(i), 1e-6);
            assertEquals(1.0, eval.correlationR2(i), 1e-6);
            assertEquals(1.0, eval.pearsonCorrelation(i), 1e-6);
            assertEquals(1.0, eval.rSquared(i), 1e-6);
        }
    }

    @Test
    public void testKnownValues() {
        double[][] labelsD = new double[][] {{1, 2, 3}, {0.1, 0.2, 0.3}, {6, 5, 4}};
        double[][] predictedD = new double[][] {{2.5, 3.2, 3.8}, {2.15, 1.3, -1.2}, {7, 4.5, 3}};

        double[] expMSE = {2.484166667, 0.966666667, 1.296666667};
        double[] expMAE = {1.516666667, 0.933333333, 1.1};
        double[] expRSE = {0.368813923, 0.246598639, 0.530937216};
        double[] expCorrs = {0.997013483, 0.968619605, 0.915603032};
        double[] expR2 = {0.63118608, 0.75340136 , 0.46906278};

        INDArray labels = Nd4j.create(labelsD);
        INDArray predicted = Nd4j.create(predictedD);

        RegressionEvaluation eval = new RegressionEvaluation(3);

        for (int xe = 0; xe < 2; xe++) {
            eval.eval(labels, predicted);

            for (int col = 0; col < 3; col++) {
                assertEquals(expMSE[col], eval.meanSquaredError(col), 1e-5);
                assertEquals(expMAE[col], eval.meanAbsoluteError(col), 1e-5);
                assertEquals(Math.sqrt(expMSE[col]), eval.rootMeanSquaredError(col), 1e-5);
                assertEquals(expRSE[col], eval.relativeSquaredError(col), 1e-5);
                assertEquals(expCorrs[col], eval.pearsonCorrelation(col), 1e-5);
                assertEquals(expR2[col], eval.rSquared(col), 1e-5);
            }

            eval.reset();
        }
    }


    @Test
    public void testRegressionEvaluationMerging() {
        Nd4j.getRandom().setSeed(12345);

        int nRows = 20;
        int nCols = 3;

        int numMinibatches = 5;
        int nEvalInstances = 4;

        List<RegressionEvaluation> list = new ArrayList<>();

        RegressionEvaluation single = new RegressionEvaluation(nCols);

        for (int i = 0; i < nEvalInstances; i++) {
            list.add(new RegressionEvaluation(nCols));
            for (int j = 0; j < numMinibatches; j++) {
                INDArray p = Nd4j.rand(nRows, nCols);
                INDArray act = Nd4j.rand(nRows, nCols);

                single.eval(act, p);

                list.get(i).eval(act, p);
            }
        }

        RegressionEvaluation merged = list.get(0);
        for (int i = 1; i < nEvalInstances; i++) {
            merged.merge(list.get(i));
        }

        double prec = 1e-5;
        for (int i = 0; i < nCols; i++) {
            assertEquals(single.correlationR2(i), merged.correlationR2(i), prec);
            assertEquals(single.meanAbsoluteError(i), merged.meanAbsoluteError(i), prec);
            assertEquals(single.meanSquaredError(i), merged.meanSquaredError(i), prec);
            assertEquals(single.relativeSquaredError(i), merged.relativeSquaredError(i), prec);
            assertEquals(single.rootMeanSquaredError(i), merged.rootMeanSquaredError(i), prec);
        }
    }

    @Test
    public void testRegressionEvalPerOutputMasking() {

        INDArray l = Nd4j.create(new double[][] {{1, 2, 3}, {10, 20, 30}, {-5, -10, -20}});

        INDArray predictions = Nd4j.zeros(l.shape());

        INDArray mask = Nd4j.create(new double[][] {{0, 1, 1}, {1, 1, 0}, {0, 1, 0}});


        RegressionEvaluation re = new RegressionEvaluation();

        re.eval(l, predictions, mask);

        double[] mse = new double[] {(10 * 10) / 1.0, (2 * 2 + 20 * 20 + 10 * 10) / 3, (3 * 3) / 1.0};

        double[] mae = new double[] {10.0, (2 + 20 + 10) / 3.0, 3.0};

        double[] rmse = new double[] {10.0, Math.sqrt((2 * 2 + 20 * 20 + 10 * 10) / 3.0), 3.0};

        for (int i = 0; i < 3; i++) {
            assertEquals(mse[i], re.meanSquaredError(i), 1e-6);
            assertEquals(mae[i], re.meanAbsoluteError(i), 1e-6);
            assertEquals(rmse[i], re.rootMeanSquaredError(i), 1e-6);
        }
    }

    @Test
    public void testRegressionEvalTimeSeriesSplit(){

        INDArray out1 = Nd4j.rand(new int[]{3, 5, 20});
        INDArray outSub1 = out1.get(all(), all(), interval(0,10));
        INDArray outSub2 = out1.get(all(), all(), interval(10, 20));

        INDArray label1 = Nd4j.rand(new int[]{3, 5, 20});
        INDArray labelSub1 = label1.get(all(), all(), interval(0,10));
        INDArray labelSub2 = label1.get(all(), all(), interval(10, 20));

        RegressionEvaluation e1 = new RegressionEvaluation();
        RegressionEvaluation e2 = new RegressionEvaluation();

        e1.eval(label1, out1);

        e2.eval(labelSub1, outSub1);
        e2.eval(labelSub2, outSub2);

        assertEquals(e1, e2);
    }
}