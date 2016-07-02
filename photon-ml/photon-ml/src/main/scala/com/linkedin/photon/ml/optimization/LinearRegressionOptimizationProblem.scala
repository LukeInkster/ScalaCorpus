/*
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linkedin.photon.ml.optimization

import scala.collection.mutable

import breeze.linalg.Vector
import org.apache.spark.rdd.RDD

import com.linkedin.photon.ml.constants.MathConst
import com.linkedin.photon.ml.data.{LabeledPoint, ObjectProvider}
import com.linkedin.photon.ml.function._
import com.linkedin.photon.ml.model.Coefficients
import com.linkedin.photon.ml.normalization.NormalizationContext
import com.linkedin.photon.ml.optimization.game.GLMOptimizationConfiguration
import com.linkedin.photon.ml.sampler.{BinaryClassificationDownSampler, DownSampler}
import com.linkedin.photon.ml.supervised.model.ModelTracker
import com.linkedin.photon.ml.supervised.regression.LinearRegressionModel

case class LinearRegressionOptimizationProblem(
    optimizer: Optimizer[LabeledPoint, TwiceDiffFunction[LabeledPoint]],
    sampler: DownSampler,
    objectiveFunction: TwiceDiffFunction[LabeledPoint],
    regularizationContext: RegularizationContext,
    regularizationWeight: Double,
    modelTrackerBuilder: Option[mutable.ListBuffer[ModelTracker]],
    treeAggregateDepth: Int,
    isComputingVariances: Boolean)
  extends GeneralizedLinearOptimizationProblem[LinearRegressionModel, TwiceDiffFunction[LabeledPoint]](
    optimizer,
    objectiveFunction,
    sampler,
    regularizationContext,
    regularizationWeight,
    modelTrackerBuilder,
    treeAggregateDepth,
    isComputingVariances) {

  /**
    * Updates properties of the objective function. Useful in cases of data-related changes or parameter sweep.
    *
    * @param normalizationContext new normalization context
    * @param regularizationWeight new regulariation weight
    * @return a new optimization problem with updated objective
    */
  override def updateObjective(
      normalizationContext: ObjectProvider[NormalizationContext],
      regularizationWeight: Double): LinearRegressionOptimizationProblem = {

    val lossFunction = new SquaredLossFunction(normalizationContext)
    lossFunction.treeAggregateDepth = treeAggregateDepth

    val objectiveFunction = TwiceDiffFunction.withRegularization(
      lossFunction,
      regularizationContext,
      regularizationWeight)

    LinearRegressionOptimizationProblem(
      optimizer,
      sampler,
      objectiveFunction,
      regularizationContext,
      regularizationWeight,
      modelTrackerBuilder,
      treeAggregateDepth,
      isComputingVariances)
  }

  /**
    * Create a default linear regression model with 0-valued coefficients
    *
    * @param dimension The dimensionality of the model coefficients
    * @return A model with zero coefficients
    */
  override def initializeZeroModel(dimension: Int): LinearRegressionModel =
    LinearRegressionOptimizationProblem.initializeZeroModel(dimension)

  /**
    * Create a model given the coefficients
    *
    * @param coefficients The coefficients parameter of each feature (and potentially including intercept)
    * @param variances The coefficient variances
    * @return A generalized linear model with coefficients parameters
    */
  override protected[optimization] def createModel(
      coefficients: Vector[Double],
      variances: Option[Vector[Double]]): LinearRegressionModel =
    new LinearRegressionModel(Coefficients(coefficients, variances))

  /**
    * Compute coefficient variances
    *
    * @param labeledPoints The training dataset
    * @param coefficients The model coefficients
    * @return The coefficient variances
    */
  override protected[optimization] def computeVariances(
      labeledPoints: RDD[LabeledPoint],
      coefficients: Vector[Double]): Option[Vector[Double]] = {

    if (isComputingVariances) {
      val broadcastCoefficients = labeledPoints.sparkContext.broadcast(coefficients)
      val variances = Some(objectiveFunction
        .hessianDiagonal(labeledPoints, broadcastCoefficients)
        .map(v => 1.0 / (v + MathConst.HIGH_PRECISION_TOLERANCE_THRESHOLD)))

      broadcastCoefficients.unpersist()
      variances
    } else {
      None
    }
  }

  /**
    * Compute coefficient variances
    *
    * @param labeledPoints The training dataset
    * @param coefficients The model coefficients
    * @return The coefficient variances
    */
  override protected[optimization] def computeVariances(
      labeledPoints: Iterable[LabeledPoint],
      coefficients: Vector[Double]): Option[Vector[Double]] = {

    if (isComputingVariances) {
      Some(objectiveFunction
        .hessianDiagonal(labeledPoints, coefficients)
        .map(v => 1.0 / (v + MathConst.HIGH_PRECISION_TOLERANCE_THRESHOLD)))
    } else {
      None
    }
  }
}

object LinearRegressionOptimizationProblem {
  val COMPUTING_VARIANCE = false

  /**
    * Build a logistic regression optimization problem
    *
    * @param configuration The optimizer configuration
    * @param treeAggregateDepth The Spark tree aggregation depth
    * @param isTrackingState Should intermediate model states be tracked?
    * @return A logistic regression optimization problem instance
    */
  protected[ml] def buildOptimizationProblem(
      configuration: GLMOptimizationConfiguration,
      treeAggregateDepth: Int = 1,
      isTrackingState: Boolean = true): LinearRegressionOptimizationProblem = {

    val optimizerConfig = configuration.optimizerConfig
    val regularizationContext = configuration.regularizationContext
    val regularizationWeight = configuration.regularizationWeight
    val downSamplingRate = configuration.downSamplingRate

    val optimizer = OptimizerFactory.twiceDiffOptimizer(optimizerConfig)
    val sampler = new BinaryClassificationDownSampler(downSamplingRate)
    val lossFunction = new SquaredLossFunction
    lossFunction.treeAggregateDepth = treeAggregateDepth
    val objectiveFunction = TwiceDiffFunction.withRegularization(
      lossFunction,
      regularizationContext,
      regularizationWeight)

    LinearRegressionOptimizationProblem(
      optimizer,
      sampler,
      objectiveFunction,
      regularizationContext,
      regularizationWeight,
      if (isTrackingState) { Some(new mutable.ListBuffer[ModelTracker]())} else { None },
      treeAggregateDepth,
      COMPUTING_VARIANCE)
  }

  def initializeZeroModel(dimension: Int): LinearRegressionModel =
    new LinearRegressionModel(Coefficients.initializeZeroCoefficients(dimension))
}
