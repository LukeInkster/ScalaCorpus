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
package com.linkedin.photon.ml.io

import com.linkedin.photon.ml.data.LabeledPoint
import com.linkedin.photon.ml.util.IndexMapLoader
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/**
  * This trait defines a general way of loading an input path into RDDs for training.
  *
  */
trait InputDataFormat {

  /**
    * Load an RDD of LabeledPoints
    *
    * @param sc
    * @param inputPath
    * @param selectedFeaturesPath
    * @param minPartitions
    * @return an RDD of LabeledPoints
    */
  def loadLabeledPoints(
      sc: SparkContext,
      inputPath: String,
      selectedFeaturesPath: Option[String],
      minPartitions: Int): RDD[LabeledPoint]

  /**
    * Provide an IndexMapLoader for the current format
    *
    * @return IndexMapLoader
    */
  def indexMapLoader(): IndexMapLoader

  def constraintFeatureMap(): Option[Map[Int, (Double, Double)]] = None
}
