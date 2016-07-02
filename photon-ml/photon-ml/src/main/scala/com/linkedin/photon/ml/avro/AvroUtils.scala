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
package com.linkedin.photon.ml.avro

import java.lang.{Double => JDouble}
import java.util.{List => JList}

import breeze.linalg.{DenseVector, SparseVector, Vector}
import com.linkedin.photon.ml.avro.data.{NameAndTerm, NameAndTermFeatureSetContainer}
import com.linkedin.photon.ml.avro.generated.{BayesianLinearModelAvro, NameTermValueAvro, LatentFactorAvro}
import com.linkedin.photon.ml.constants.MathConst
import com.linkedin.photon.ml.model.Coefficients
import com.linkedin.photon.ml.supervised.model.GeneralizedLinearModel
import com.linkedin.photon.ml.util.{Utils, VectorUtils}
import org.apache.avro.generic.GenericRecord
import org.apache.avro.mapred.{AvroInputFormat, AvroWrapper}
import org.apache.hadoop.io.NullWritable
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.{Map, Set, mutable}

// TODO: Change the scope of all functions in the object to [[com.linkedin.photon.ml.avro]] after Avro related
// classes/functons are decoupled from the rest of code

/**
  * Some basic functions to read/write Avro's [[GenericRecord]] from/to HDFS.
  */
object AvroUtils {

  /**
    * Read Avro generic records from the input paths
    *
    * @param sc The Spark context
    * @param inputPaths The input paths of the generic records
    * @param minPartitions Minimum number of partitions of the output RDD
    * @return A [[RDD]] of Avro records of type [[GenericRecord]] read from the specified input paths
    */
  protected[ml] def readAvroFiles(sc: SparkContext, inputPaths: Seq[String], minPartitions: Int)
  : RDD[GenericRecord] = {

    assert(inputPaths.nonEmpty, "The number of input paths is zero.")
    val minPartitionsPerPath = math.ceil(1.0 * minPartitions / inputPaths.length).toInt
    inputPaths.map { path =>
      sc.hadoopFile[AvroWrapper[GenericRecord], NullWritable, AvroInputFormat[GenericRecord]](path,
        minPartitionsPerPath)
    }.reduce(_ ++ _).map(_._1.datum())
  }

  /**
    * Convert the vector of type [[Vector[Double]]] to an array of Avro records of type [[NameTermValueAvro]]
    *
    * @param vector The input vector
    * @param featureMap A map of feature index of type [[Int]] to feature name of type [[NameAndTerm]]
    * @return An array of Avro records that contains the information of the input vector
    */
  private def convertVectorAsArrayOfNameTermValueAvros(vector: Vector[Double], featureMap: Map[Int, NameAndTerm])
  : Array[NameTermValueAvro] = {

    vector match {
      case dense: DenseVector[Double] =>
        dense.toArray.zipWithIndex.map(_.swap).filter { case (key, value) =>
          math.abs(value) > MathConst.LOW_PRECISION_TOLERANCE_THRESHOLD
        }
          .sortWith((p1, p2) => math.abs(p1._2) > math.abs(p2._2)).map { case (index, value) =>
          featureMap.get(index) match {
            case Some(NameAndTerm(name, term)) =>
              NameTermValueAvro.newBuilder().setName(name).setTerm(term).setValue(value).build()
            case None =>
              throw new NoSuchElementException(s"Feature index $index not found in the feature map")
          }
        }
      case sparse: SparseVector[Double] =>
        sparse.activeIterator.filter { case (key, value) =>
          math.abs(value) > MathConst.LOW_PRECISION_TOLERANCE_THRESHOLD
        }.toArray
          .sortWith((p1, p2) => math.abs(p1._2) > math.abs(p2._2)).map { case (index, value) =>
          featureMap.get(index) match {
            case Some(NameAndTerm(name, term)) =>
              NameTermValueAvro.newBuilder().setName(name).setTerm(term).setValue(value).build()
            case None =>
              throw new NoSuchElementException(s"Feature index $index not found in the feature map")
          }
        }
    }
  }

  /**
    * Read the nameAndTerm of type [[NameAndTerm]] from Avro record of type [[GenericRecord]]
    *
    * @param record The input Avro record
    * @return The nameAndTerm parsed from the Avro record
    */
  protected[avro] def readNameAndTermFromGenericRecord(record: GenericRecord): NameAndTerm = {
    val name = Utils.getStringAvro(record, AvroFieldNames.NAME)
    val term = Utils.getStringAvro(record, AvroFieldNames.TERM, isNullOK = true)
    NameAndTerm(name, term)
  }

  /**
    * Parse a set of nameAndTerm of type [[NameAndTerm]] from a RDD of Avro record of type [[GenericRecord]] with the
    * user specified feature section keys
    *
    * @param genericRecords The input Avro records
    * @param featureSectionKey The user specified feature section keys
    * @return A set of nameAndTerms parsed from the input Avro records
    */
  protected[avro] def readNameAndTermSetFromGenericRecords(
    genericRecords: RDD[GenericRecord],
    featureSectionKey: String): Set[NameAndTerm] = {

    genericRecords.flatMap(_.get(featureSectionKey) match {
      case recordList: JList[_] => recordList.asScala.map {
        case record: GenericRecord => AvroUtils.readNameAndTermFromGenericRecord(record)
        case any => throw new IllegalArgumentException(s"$any in features list is not a record")
      }
      case _ => throw new IllegalArgumentException(s"$featureSectionKey is not a list (and might be null)")
    }).distinct().collect().toSet
  }

  /**
    * Generate the [[NameAndTermFeatureSetContainer]] from a [[RDD]] of [[GenericRecord]]s.
    *
    * @param genericRecords The input [[RDD]] of [[GenericRecord]]s.
    * @param featureSectionKeys The set of feature section keys of interest in the input generic records
    * @return The generated [[NameAndTermFeatureSetContainer]]
    */
  protected[avro] def readNameAndTermFeatureSetContainerFromGenericRecords(
    genericRecords: RDD[GenericRecord],
    featureSectionKeys: Set[String]): NameAndTermFeatureSetContainer = {

    val nameAndTermFeatureSets = featureSectionKeys.map { featureSectionKey =>
      (featureSectionKey, AvroUtils.readNameAndTermSetFromGenericRecords(genericRecords, featureSectionKey))
    }.toMap
    new NameAndTermFeatureSetContainer(nameAndTermFeatureSets)
  }

  /**
    * Convert the coefficients of type [[Coefficients]] to Avro record of type [[BayesianLinearModelAvro]]
    *
    * @param modelId The model's id
    * @param intToNameAndTermMap The map from feature index of type [[Int]] to feature name of type [[NameAndTerm]]
    * @return The Avro record that contains the information of the input coefficients
    */
  protected[avro] def convertGLMModelToBayesianLinearModelAvro(
      model: GeneralizedLinearModel,
      modelId: String,
      intToNameAndTermMap: Map[Int, NameAndTerm]): BayesianLinearModelAvro = {

    val modelCoefficients = model.coefficients
    val meansAvros = convertVectorAsArrayOfNameTermValueAvros(modelCoefficients.means, intToNameAndTermMap)
    val variancesAvrosOption = modelCoefficients
      .variancesOption
      .map(convertVectorAsArrayOfNameTermValueAvros(_, intToNameAndTermMap))
    // TODO: Output type of model.
    val avroFile = BayesianLinearModelAvro
      .newBuilder()
      .setModelId(modelId)
      .setModelClass(model.getClass.getName)
      .setLossFunction("")
      .setMeans(meansAvros.toList)

    if (variancesAvrosOption.isDefined) {
      avroFile.setVariances(variancesAvrosOption.get.toList)
    }
    avroFile.build()
  }

  /**
    * Convert the Avro record of type [[BayesianLinearModelAvro]] to the model type [[GeneralizedLinearModel]]
    *
    * @param bayesianLinearModelAvro The input Avro record
    * @param nameAndTermToIntMap The map from feature name of type [[NameAndTerm]] to feature index of type [[Int]]
    * @return The generalized linear model converted from the Avro record
    */
  protected[avro] def convertBayesianLinearModelAvroToGLM(
      bayesianLinearModelAvro: BayesianLinearModelAvro,
      nameAndTermToIntMap: Map[NameAndTerm, Int]): GeneralizedLinearModel = {

    val meansAvros = bayesianLinearModelAvro.getMeans
    val modelClass = bayesianLinearModelAvro.getModelClass.toString
    val indexAndValueArrayBuffer = new mutable.ArrayBuffer[(Int, Double)]

    val iterator = meansAvros.iterator()
    while (iterator.hasNext) {
      val feature = iterator.next()
      val name = feature.getName.toString
      val term = feature.getTerm.toString
      val nameAndTerm = NameAndTerm(name, term)
      if (nameAndTermToIntMap.contains(nameAndTerm)) {
        val value = feature.getValue
        val index = nameAndTermToIntMap.getOrElse(nameAndTerm,
          throw new NoSuchElementException(s"nameAndTerm $nameAndTerm not found in the feature map"))
        indexAndValueArrayBuffer += ((index, value))
      }
    }

    val maxIndex = nameAndTermToIntMap.values.max
    val length = maxIndex + 1
    val coefficients = Coefficients(
      VectorUtils.convertIndexAndValuePairArrayToVector(indexAndValueArrayBuffer.toArray, length))

    // Load an instantiate the model
    try {
      Class.forName(modelClass)
        .getConstructor(classOf[Coefficients])
        .newInstance(coefficients)
        .asInstanceOf[GeneralizedLinearModel]

    } catch {
      case e: Exception =>
        throw new IllegalArgumentException(
          s"Error loading model: model class $modelClass couldn't be loaded. You may need to retrain the model.", e)
    }
  }

  /**
    * Convert the latent factor of type [[Vector[Double]]] to Avro record of type [[LatentFactorAvro]]
    *
    * @param effectId The id of the latent factor, e.g., row Id, col Id, user Id or itemId
    * @param latentFactor The latent factor of the matrix factorization model
    * @return The Avro record that contains the information of the input latent factor
    */
  protected[avro] def convertLatentFactorToLatentFactorAvro(effectId: String, latentFactor: Vector[Double])
    : LatentFactorAvro = {

    val latentFactorAsList = latentFactor.toArray.map(JDouble.valueOf).toList
    val avroFile = LatentFactorAvro.newBuilder().setEffectId(effectId).setLatentFactor(latentFactorAsList)
    avroFile.build()
  }

  /**
    * Convert the given Avro record of type [[LatentFactorAvro]] to the latent factor of type [[Vector[Double]]]
    *
    * @param latentFactorAvro The given Avro record
    * @return The (effectId, latentFactor) pair converted from the input Avro record
    */
  protected[avro] def convertLatentFactorAvroToLatentFactor(latentFactorAvro: LatentFactorAvro)
    : (String, Vector[Double]) = {

    val effectId = latentFactorAvro.getEffectId.toString
    val latentFactor = new DenseVector[Double](latentFactorAvro.getLatentFactor.toArray().map(_.asInstanceOf[Double]))
    (effectId, latentFactor)
  }
}
