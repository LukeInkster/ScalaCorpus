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


package org.apache.spark.sql.execution

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.types._
import org.apache.spark.util.collection.unsafe.sort.{PrefixComparator, PrefixComparators}

object SortPrefixUtils {

  /**
   * A dummy prefix comparator which always claims that prefixes are equal. This is used in cases
   * where we don't know how to generate or compare prefixes for a SortOrder.
   */
  private object NoOpPrefixComparator extends PrefixComparator {
    override def compare(prefix1: Long, prefix2: Long): Int = 0
  }

  /**
   * Dummy sort prefix result to use for empty rows.
   */
  private val emptyPrefix = new UnsafeExternalRowSorter.PrefixComputer.Prefix

  def getPrefixComparator(sortOrder: SortOrder): PrefixComparator = {
    sortOrder.dataType match {
      case StringType =>
        if (sortOrder.isAscending) PrefixComparators.STRING else PrefixComparators.STRING_DESC
      case BinaryType =>
        if (sortOrder.isAscending) PrefixComparators.BINARY else PrefixComparators.BINARY_DESC
      case BooleanType | ByteType | ShortType | IntegerType | LongType | DateType | TimestampType =>
        if (sortOrder.isAscending) PrefixComparators.LONG else PrefixComparators.LONG_DESC
      case dt: DecimalType if dt.precision - dt.scale <= Decimal.MAX_LONG_DIGITS =>
        if (sortOrder.isAscending) PrefixComparators.LONG else PrefixComparators.LONG_DESC
      case FloatType | DoubleType =>
        if (sortOrder.isAscending) PrefixComparators.DOUBLE else PrefixComparators.DOUBLE_DESC
      case dt: DecimalType =>
        if (sortOrder.isAscending) PrefixComparators.DOUBLE else PrefixComparators.DOUBLE_DESC
      case _ => NoOpPrefixComparator
    }
  }

  /**
   * Creates the prefix comparator for the first field in the given schema, in ascending order.
   */
  def getPrefixComparator(schema: StructType): PrefixComparator = {
    if (schema.nonEmpty) {
      val field = schema.head
      getPrefixComparator(SortOrder(BoundReference(0, field.dataType, field.nullable), Ascending))
    } else {
      new PrefixComparator {
        override def compare(prefix1: Long, prefix2: Long): Int = 0
      }
    }
  }

  /**
   * Returns whether the specified SortOrder can be satisfied with a radix sort on the prefix.
   */
  def canSortFullyWithPrefix(sortOrder: SortOrder): Boolean = {
    sortOrder.dataType match {
      case BooleanType | ByteType | ShortType | IntegerType | LongType | DateType |
           TimestampType | FloatType | DoubleType =>
        true
      case dt: DecimalType if dt.precision <= Decimal.MAX_LONG_DIGITS =>
        true
      case _ =>
        false
    }
  }

  /**
   * Returns whether the fully sorting on the specified key field is possible with radix sort.
   */
  def canSortFullyWithPrefix(field: StructField): Boolean = {
    canSortFullyWithPrefix(SortOrder(BoundReference(0, field.dataType, field.nullable), Ascending))
  }

  /**
   * Creates the prefix computer for the first field in the given schema, in ascending order.
   */
  def createPrefixGenerator(schema: StructType): UnsafeExternalRowSorter.PrefixComputer = {
    if (schema.nonEmpty) {
      val boundReference = BoundReference(0, schema.head.dataType, nullable = true)
      val prefixExpr = SortPrefix(SortOrder(boundReference, Ascending))
      val prefixProjection = UnsafeProjection.create(prefixExpr)
      new UnsafeExternalRowSorter.PrefixComputer {
        private val result = new UnsafeExternalRowSorter.PrefixComputer.Prefix
        override def computePrefix(row: InternalRow):
            UnsafeExternalRowSorter.PrefixComputer.Prefix = {
          val prefix = prefixProjection.apply(row)
          if (prefix.isNullAt(0)) {
            result.isNull = true
            result.value = prefixExpr.nullValue
          } else {
            result.isNull = false
            result.value = prefix.getLong(0)
          }
          result
        }
      }
    } else {
      new UnsafeExternalRowSorter.PrefixComputer {
        override def computePrefix(row: InternalRow):
            UnsafeExternalRowSorter.PrefixComputer.Prefix = {
          emptyPrefix
        }
      }
    }
  }
}
