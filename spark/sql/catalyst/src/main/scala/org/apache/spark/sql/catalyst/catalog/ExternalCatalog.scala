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

package org.apache.spark.sql.catalyst.catalog

import org.apache.spark.sql.catalyst.analysis.NoSuchDatabaseException


/**
 * Interface for the system catalog (of columns, partitions, tables, and databases).
 *
 * This is only used for non-temporary items, and implementations must be thread-safe as they
 * can be accessed in multiple threads. This is an external catalog because it is expected to
 * interact with external systems.
 *
 * Implementations should throw [[NoSuchDatabaseException]] when databases don't exist.
 */
abstract class ExternalCatalog {
  import CatalogTypes.TablePartitionSpec

  protected def requireDbExists(db: String): Unit = {
    if (!databaseExists(db)) {
      throw new NoSuchDatabaseException(db)
    }
  }

  // --------------------------------------------------------------------------
  // Databases
  // --------------------------------------------------------------------------

  def createDatabase(dbDefinition: CatalogDatabase, ignoreIfExists: Boolean): Unit

  def dropDatabase(db: String, ignoreIfNotExists: Boolean, cascade: Boolean): Unit

  /**
   * Alter a database whose name matches the one specified in `dbDefinition`,
   * assuming the database exists.
   *
   * Note: If the underlying implementation does not support altering a certain field,
   * this becomes a no-op.
   */
  def alterDatabase(dbDefinition: CatalogDatabase): Unit

  def getDatabase(db: String): CatalogDatabase

  def databaseExists(db: String): Boolean

  def listDatabases(): Seq[String]

  def listDatabases(pattern: String): Seq[String]

  def setCurrentDatabase(db: String): Unit

  // --------------------------------------------------------------------------
  // Tables
  // --------------------------------------------------------------------------

  def createTable(db: String, tableDefinition: CatalogTable, ignoreIfExists: Boolean): Unit

  def dropTable(db: String, table: String, ignoreIfNotExists: Boolean): Unit

  def renameTable(db: String, oldName: String, newName: String): Unit

  /**
   * Alter a table whose name that matches the one specified in `tableDefinition`,
   * assuming the table exists.
   *
   * Note: If the underlying implementation does not support altering a certain field,
   * this becomes a no-op.
   */
  def alterTable(db: String, tableDefinition: CatalogTable): Unit

  def getTable(db: String, table: String): CatalogTable

  def getTableOption(db: String, table: String): Option[CatalogTable]

  def tableExists(db: String, table: String): Boolean

  def listTables(db: String): Seq[String]

  def listTables(db: String, pattern: String): Seq[String]

  def loadTable(
      db: String,
      table: String,
      loadPath: String,
      isOverwrite: Boolean,
      holdDDLTime: Boolean): Unit

  def loadPartition(
      db: String,
      table: String,
      loadPath: String,
      partition: TablePartitionSpec,
      isOverwrite: Boolean,
      holdDDLTime: Boolean,
      inheritTableSpecs: Boolean,
      isSkewedStoreAsSubdir: Boolean): Unit

  // --------------------------------------------------------------------------
  // Partitions
  // --------------------------------------------------------------------------

  def createPartitions(
      db: String,
      table: String,
      parts: Seq[CatalogTablePartition],
      ignoreIfExists: Boolean): Unit

  def dropPartitions(
      db: String,
      table: String,
      parts: Seq[TablePartitionSpec],
      ignoreIfNotExists: Boolean): Unit

  /**
   * Override the specs of one or many existing table partitions, assuming they exist.
   * This assumes index i of `specs` corresponds to index i of `newSpecs`.
   */
  def renamePartitions(
      db: String,
      table: String,
      specs: Seq[TablePartitionSpec],
      newSpecs: Seq[TablePartitionSpec]): Unit

  /**
   * Alter one or many table partitions whose specs that match those specified in `parts`,
   * assuming the partitions exist.
   *
   * Note: If the underlying implementation does not support altering a certain field,
   * this becomes a no-op.
   */
  def alterPartitions(
      db: String,
      table: String,
      parts: Seq[CatalogTablePartition]): Unit

  def getPartition(db: String, table: String, spec: TablePartitionSpec): CatalogTablePartition

  /**
   * List the metadata of all partitions that belong to the specified table, assuming it exists.
   *
   * A partial partition spec may optionally be provided to filter the partitions returned.
   * For instance, if there exist partitions (a='1', b='2'), (a='1', b='3') and (a='2', b='4'),
   * then a partial spec of (a='1') will return the first two only.
   * @param db database name
   * @param table table name
   * @param partialSpec  partition spec
   */
  def listPartitions(
      db: String,
      table: String,
      partialSpec: Option[TablePartitionSpec] = None): Seq[CatalogTablePartition]

  // --------------------------------------------------------------------------
  // Functions
  // --------------------------------------------------------------------------

  def createFunction(db: String, funcDefinition: CatalogFunction): Unit

  def dropFunction(db: String, funcName: String): Unit

  def renameFunction(db: String, oldName: String, newName: String): Unit

  def getFunction(db: String, funcName: String): CatalogFunction

  def functionExists(db: String, funcName: String): Boolean

  def listFunctions(db: String, pattern: String): Seq[String]

}
