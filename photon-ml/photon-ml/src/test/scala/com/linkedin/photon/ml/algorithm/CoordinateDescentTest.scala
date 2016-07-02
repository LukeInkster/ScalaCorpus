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
package com.linkedin.photon.ml.algorithm

import com.linkedin.photon.ml.constants.StorageLevel
import com.linkedin.photon.ml.data.{DataSet, GameDatum, KeyValueScore, LabeledPoint}
import com.linkedin.photon.ml.evaluation.Evaluator
import com.linkedin.photon.ml.function.DiffFunction
import com.linkedin.photon.ml.optimization.game.OptimizationTracker
import com.linkedin.photon.ml.model.{GAMEModel, DatumScoringModel}
import com.linkedin.photon.ml.supervised.model.GeneralizedLinearModel
import com.linkedin.photon.ml.util.PhotonLogger

import breeze.linalg.Vector
import org.apache.spark.rdd.RDD
import org.mockito.Matchers
import org.mockito.Mockito._
import org.testng.Assert._
import org.testng.annotations.{DataProvider, Test}

/**
  * Tests for the CoordinateDescent implementaion
  */
class CoordinateDescentTest {

  @DataProvider
  def coordinateCountProvider(): Array[Array[Integer]] = {
    (1 to 5).map(x => Array(Int.box(x))).toArray
  }

  @Test(dataProvider = "coordinateCountProvider")
  def testRun(coordinateCount: Int): Unit = {
    val numIterations = 10

    // Create Coordinate mocks
    val coordinateIds = (0 until coordinateCount).map("coordinate" + _)
    val coordinates: Seq[(String,
        FixedEffectCoordinate[_ <: GeneralizedLinearModel, _ <: DiffFunction[LabeledPoint]])] =
      coordinateIds.map { coordinateId =>
        val coordinate = mock(
          classOf[FixedEffectCoordinate[_ <: GeneralizedLinearModel, _ <: DiffFunction[LabeledPoint]]])

        (coordinateId, coordinate)
      }

    // Other mocks
    val evaluator = mock(classOf[Evaluator])
    val logger = mock(classOf[PhotonLogger])
    val gameModel = mock(classOf[GAMEModel])
    val tracker = mock(classOf[OptimizationTracker])
    val score = mock(classOf[KeyValueScore])
    val models = coordinates.map { _ =>
      mock(classOf[DatumScoringModel])
    }

    // KeyValueScore mock setup
    doReturn(score).when(score).+(score)
    doReturn(score).when(score).setName(Matchers.any(classOf[String]))
    doReturn(score).when(score).persistRDD(Matchers.any())

    // Per-coordinate mock setup
    (coordinates zip models).map { case ((coordinateId, coordinate), model) =>
      // GAMEModel mock setup
      doReturn(gameModel).when(gameModel).updateModel(coordinateId, model)
      doReturn(Some(model)).when(gameModel).getModel(coordinateId)

      // Coordinate mock setup
      doReturn(score).when(coordinate).score(model)
      doReturn((model, tracker)).when(coordinate).updateModel(model)
      doReturn((model, tracker)).when(coordinate).updateModel(model, score)
    }

    // Run coordinate descent
    val coordinateDescent = new CoordinateDescent(coordinates, evaluator, None, logger)
    coordinateDescent.run(numIterations, gameModel)

    // Verify the calls to updateModel
    if (coordinates.length == 1) {
      verify(coordinates(0)._2, times(numIterations)).updateModel(models(0))

    } else {
      (coordinates zip models).map { case ((coordinateId, coordinate), model) =>
        verify(coordinate, times(numIterations)).updateModel(model, score)
      }
    }
  }
}
