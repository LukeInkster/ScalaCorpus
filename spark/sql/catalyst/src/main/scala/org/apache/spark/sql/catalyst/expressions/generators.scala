/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.expressions

import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.{CatalystTypeConverters, InternalRow}
import org.apache.spark.sql.catalyst.analysis.TypeCheckResult
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.util.{ArrayData, MapData}
import org.apache.spark.sql.types._

/**
 * An expression that produces zero or more rows given a single input row.
 *
 * Generators produce multiple output rows instead of a single value like other expressions,
 * and thus they must have a schema to associate with the rows that are output.
 *
 * However, unlike row producing relational operators, which are either leaves or determine their
 * output schema functionally from their input, generators can contain other expressions that
 * might result in their modification by rules.  This structure means that they might be copied
 * multiple times after first determining their output schema. If a new output schema is created for
 * each copy references up the tree might be rendered invalid. As a result generators must
 * instead define a function `makeOutput` which is called only once when the schema is first
 * requested.  The attributes produced by this function will be automatically copied anytime rules
 * result in changes to the Generator or its children.
 */
trait Generator extends Expression {

  override def dataType: DataType = ArrayType(elementSchema)

  override def foldable: Boolean = false

  override def nullable: Boolean = false

  /**
   * The output element schema.
   */
  def elementSchema: StructType

  /** Should be implemented by child classes to perform specific Generators. */
  override def eval(input: InternalRow): TraversableOnce[InternalRow]

  /**
   * Notifies that there are no more rows to process, clean up code, and additional
   * rows can be made here.
   */
  def terminate(): TraversableOnce[InternalRow] = Nil
}

/**
 * A generator that produces its output using the provided lambda function.
 */
case class UserDefinedGenerator(
    elementSchema: StructType,
    function: Row => TraversableOnce[InternalRow],
    children: Seq[Expression])
  extends Generator with CodegenFallback {

  @transient private[this] var inputRow: InterpretedProjection = _
  @transient private[this] var convertToScala: (InternalRow) => Row = _

  private def initializeConverters(): Unit = {
    inputRow = new InterpretedProjection(children)
    convertToScala = {
      val inputSchema = StructType(children.map(e => StructField(e.simpleString, e.dataType, true)))
      CatalystTypeConverters.createToScalaConverter(inputSchema)
    }.asInstanceOf[InternalRow => Row]
  }

  override def eval(input: InternalRow): TraversableOnce[InternalRow] = {
    if (inputRow == null) {
      initializeConverters()
    }
    // Convert the objects into Scala Type before calling function, we need schema to support UDT
    function(convertToScala(inputRow(input)))
  }

  override def toString: String = s"UserDefinedGenerator(${children.mkString(",")})"
}

/**
 * A base class for Explode and PosExplode
 */
abstract class ExplodeBase(child: Expression, position: Boolean)
  extends UnaryExpression with Generator with CodegenFallback with Serializable {

  override def children: Seq[Expression] = child :: Nil

  override def checkInputDataTypes(): TypeCheckResult = {
    if (child.dataType.isInstanceOf[ArrayType] || child.dataType.isInstanceOf[MapType]) {
      TypeCheckResult.TypeCheckSuccess
    } else {
      TypeCheckResult.TypeCheckFailure(
        s"input to function explode should be array or map type, not ${child.dataType}")
    }
  }

  // hive-compatible default alias for explode function ("col" for array, "key", "value" for map)
  override def elementSchema: StructType = child.dataType match {
    case ArrayType(et, containsNull) =>
      if (position) {
        new StructType()
          .add("pos", IntegerType, false)
          .add("col", et, containsNull)
      } else {
        new StructType()
          .add("col", et, containsNull)
      }
    case MapType(kt, vt, valueContainsNull) =>
      if (position) {
        new StructType()
          .add("pos", IntegerType, false)
          .add("key", kt, false)
          .add("value", vt, valueContainsNull)
      } else {
        new StructType()
          .add("key", kt, false)
          .add("value", vt, valueContainsNull)
      }
  }

  override def eval(input: InternalRow): TraversableOnce[InternalRow] = {
    child.dataType match {
      case ArrayType(et, _) =>
        val inputArray = child.eval(input).asInstanceOf[ArrayData]
        if (inputArray == null) {
          Nil
        } else {
          val rows = new Array[InternalRow](inputArray.numElements())
          inputArray.foreach(et, (i, e) => {
            rows(i) = if (position) InternalRow(i, e) else InternalRow(e)
          })
          rows
        }
      case MapType(kt, vt, _) =>
        val inputMap = child.eval(input).asInstanceOf[MapData]
        if (inputMap == null) {
          Nil
        } else {
          val rows = new Array[InternalRow](inputMap.numElements())
          var i = 0
          inputMap.foreach(kt, vt, (k, v) => {
            rows(i) = if (position) InternalRow(i, k, v) else InternalRow(k, v)
            i += 1
          })
          rows
        }
    }
  }
}

/**
 * Given an input array produces a sequence of rows for each value in the array.
 *
 * {{{
 *   SELECT explode(array(10,20)) ->
 *   10
 *   20
 * }}}
 */
// scalastyle:off line.size.limit
@ExpressionDescription(
  usage = "_FUNC_(a) - Separates the elements of array a into multiple rows, or the elements of map a into multiple rows and columns.",
  extended = "> SELECT _FUNC_(array(10,20));\n  10\n  20")
// scalastyle:on line.size.limit
case class Explode(child: Expression) extends ExplodeBase(child, position = false)

/**
 * Given an input array produces a sequence of rows for each position and value in the array.
 *
 * {{{
 *   SELECT posexplode(array(10,20)) ->
 *   0  10
 *   1  20
 * }}}
 */
// scalastyle:off line.size.limit
@ExpressionDescription(
  usage = "_FUNC_(a) - Separates the elements of array a into multiple rows with positions, or the elements of a map into multiple rows and columns with positions.",
  extended = "> SELECT _FUNC_(array(10,20));\n  0\t10\n  1\t20")
// scalastyle:on line.size.limit
case class PosExplode(child: Expression) extends ExplodeBase(child, position = true)
