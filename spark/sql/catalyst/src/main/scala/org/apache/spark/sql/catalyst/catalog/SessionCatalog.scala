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

import javax.annotation.concurrent.GuardedBy

import scala.collection.mutable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import org.apache.spark.internal.Logging
import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.{CatalystConf, SimpleCatalystConf}
import org.apache.spark.sql.catalyst.{FunctionIdentifier, TableIdentifier}
import org.apache.spark.sql.catalyst.analysis._
import org.apache.spark.sql.catalyst.analysis.FunctionRegistry.FunctionBuilder
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionInfo}
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, SubqueryAlias}
import org.apache.spark.sql.catalyst.util.StringUtils

/**
 * An internal catalog that is used by a Spark Session. This internal catalog serves as a
 * proxy to the underlying metastore (e.g. Hive Metastore) and it also manages temporary
 * tables and functions of the Spark Session that it belongs to.
 *
 * This class must be thread-safe.
 */
class SessionCatalog(
    externalCatalog: ExternalCatalog,
    functionResourceLoader: FunctionResourceLoader,
    functionRegistry: FunctionRegistry,
    conf: CatalystConf,
    hadoopConf: Configuration) extends Logging {
  import CatalogTypes.TablePartitionSpec

  // For testing only.
  def this(
      externalCatalog: ExternalCatalog,
      functionRegistry: FunctionRegistry,
      conf: CatalystConf) {
    this(
      externalCatalog,
      DummyFunctionResourceLoader,
      functionRegistry,
      conf,
      new Configuration())
  }

  // For testing only.
  def this(externalCatalog: ExternalCatalog) {
    this(externalCatalog, new SimpleFunctionRegistry, new SimpleCatalystConf(true))
  }

  /** List of temporary tables, mapping from table name to their logical plan. */
  @GuardedBy("this")
  protected val tempTables = new mutable.HashMap[String, LogicalPlan]

  // Note: we track current database here because certain operations do not explicitly
  // specify the database (e.g. DROP TABLE my_table). In these cases we must first
  // check whether the temporary table or function exists, then, if not, operate on
  // the corresponding item in the current database.
  @GuardedBy("this")
  protected var currentDb = {
    val defaultName = "default"
    val defaultDbDefinition =
      CatalogDatabase(defaultName, "default database", conf.warehousePath, Map())
    // Initialize default database if it doesn't already exist
    createDatabase(defaultDbDefinition, ignoreIfExists = true)
    formatDatabaseName(defaultName)
  }

  /**
   * Format table name, taking into account case sensitivity.
   */
  protected[this] def formatTableName(name: String): String = {
    if (conf.caseSensitiveAnalysis) name else name.toLowerCase
  }

  /**
   * Format database name, taking into account case sensitivity.
   */
  protected[this] def formatDatabaseName(name: String): String = {
    if (conf.caseSensitiveAnalysis) name else name.toLowerCase
  }

  /**
   * This method is used to make the given path qualified before we
   * store this path in the underlying external catalog. So, when a path
   * does not contain a scheme, this path will not be changed after the default
   * FileSystem is changed.
   */
  private def makeQualifiedPath(path: String): Path = {
    val hadoopPath = new Path(path)
    val fs = hadoopPath.getFileSystem(hadoopConf)
    fs.makeQualified(hadoopPath)
  }

  private def requireDbExists(db: String): Unit = {
    if (!databaseExists(db)) {
      throw new NoSuchDatabaseException(db)
    }
  }

  private def requireTableExists(name: TableIdentifier): Unit = {
    if (!tableExists(name)) {
      val db = name.database.getOrElse(currentDb)
      throw new NoSuchTableException(db = db, table = name.table)
    }
  }

  private def requireTableNotExists(name: TableIdentifier): Unit = {
    if (tableExists(name)) {
      val db = name.database.getOrElse(currentDb)
      throw new TableAlreadyExistsException(db = db, table = name.table)
    }
  }
  // ----------------------------------------------------------------------------
  // Databases
  // ----------------------------------------------------------------------------
  // All methods in this category interact directly with the underlying catalog.
  // ----------------------------------------------------------------------------

  def createDatabase(dbDefinition: CatalogDatabase, ignoreIfExists: Boolean): Unit = {
    val qualifiedPath = makeQualifiedPath(dbDefinition.locationUri).toString
    val dbName = formatDatabaseName(dbDefinition.name)
    externalCatalog.createDatabase(
      dbDefinition.copy(name = dbName, locationUri = qualifiedPath),
      ignoreIfExists)
  }

  def dropDatabase(db: String, ignoreIfNotExists: Boolean, cascade: Boolean): Unit = {
    val dbName = formatDatabaseName(db)
    if (dbName == "default") {
      throw new AnalysisException(s"Can not drop default database")
    }
    externalCatalog.dropDatabase(dbName, ignoreIfNotExists, cascade)
  }

  def alterDatabase(dbDefinition: CatalogDatabase): Unit = {
    val dbName = formatDatabaseName(dbDefinition.name)
    requireDbExists(dbName)
    externalCatalog.alterDatabase(dbDefinition.copy(name = dbName))
  }

  def getDatabaseMetadata(db: String): CatalogDatabase = {
    val dbName = formatDatabaseName(db)
    requireDbExists(dbName)
    externalCatalog.getDatabase(dbName)
  }

  def databaseExists(db: String): Boolean = {
    val dbName = formatDatabaseName(db)
    externalCatalog.databaseExists(dbName)
  }

  def listDatabases(): Seq[String] = {
    externalCatalog.listDatabases()
  }

  def listDatabases(pattern: String): Seq[String] = {
    externalCatalog.listDatabases(pattern)
  }

  def getCurrentDatabase: String = synchronized { currentDb }

  def setCurrentDatabase(db: String): Unit = {
    val dbName = formatDatabaseName(db)
    requireDbExists(dbName)
    synchronized { currentDb = dbName }
  }

  /**
   * Get the path for creating a non-default database when database location is not provided
   * by users.
   */
  def getDefaultDBPath(db: String): String = {
    val database = formatDatabaseName(db)
    new Path(new Path(conf.warehousePath), database + ".db").toString
  }

  // ----------------------------------------------------------------------------
  // Tables
  // ----------------------------------------------------------------------------
  // There are two kinds of tables, temporary tables and metastore tables.
  // Temporary tables are isolated across sessions and do not belong to any
  // particular database. Metastore tables can be used across multiple
  // sessions as their metadata is persisted in the underlying catalog.
  // ----------------------------------------------------------------------------

  // ----------------------------------------------------
  // | Methods that interact with metastore tables only |
  // ----------------------------------------------------

  /**
   * Create a metastore table in the database specified in `tableDefinition`.
   * If no such database is specified, create it in the current database.
   */
  def createTable(tableDefinition: CatalogTable, ignoreIfExists: Boolean): Unit = {
    val db = formatDatabaseName(tableDefinition.identifier.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableDefinition.identifier.table)
    val newTableDefinition = tableDefinition.copy(identifier = TableIdentifier(table, Some(db)))
    requireDbExists(db)
    externalCatalog.createTable(db, newTableDefinition, ignoreIfExists)
  }

  /**
   * Alter the metadata of an existing metastore table identified by `tableDefinition`.
   *
   * If no database is specified in `tableDefinition`, assume the table is in the
   * current database.
   *
   * Note: If the underlying implementation does not support altering a certain field,
   * this becomes a no-op.
   */
  def alterTable(tableDefinition: CatalogTable): Unit = {
    val db = formatDatabaseName(tableDefinition.identifier.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableDefinition.identifier.table)
    val tableIdentifier = TableIdentifier(table, Some(db))
    val newTableDefinition = tableDefinition.copy(identifier = tableIdentifier)
    requireDbExists(db)
    requireTableExists(tableIdentifier)
    externalCatalog.alterTable(db, newTableDefinition)
  }

  /**
   * Retrieve the metadata of an existing metastore table.
   * If no database is specified, assume the table is in the current database.
   * If the specified table is not found in the database then a [[NoSuchTableException]] is thrown.
   */
  def getTableMetadata(name: TableIdentifier): CatalogTable = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(name.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Some(db)))
    externalCatalog.getTable(db, table)
  }

  /**
   * Retrieve the metadata of an existing metastore table.
   * If no database is specified, assume the table is in the current database.
   * If the specified table is not found in the database then return None if it doesn't exist.
   */
  def getTableMetadataOption(name: TableIdentifier): Option[CatalogTable] = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(name.table)
    requireDbExists(db)
    externalCatalog.getTableOption(db, table)
  }

  /**
   * Load files stored in given path into an existing metastore table.
   * If no database is specified, assume the table is in the current database.
   * If the specified table is not found in the database then a [[NoSuchTableException]] is thrown.
   */
  def loadTable(
      name: TableIdentifier,
      loadPath: String,
      isOverwrite: Boolean,
      holdDDLTime: Boolean): Unit = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(name.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Some(db)))
    externalCatalog.loadTable(db, table, loadPath, isOverwrite, holdDDLTime)
  }

  /**
   * Load files stored in given path into the partition of an existing metastore table.
   * If no database is specified, assume the table is in the current database.
   * If the specified table is not found in the database then a [[NoSuchTableException]] is thrown.
   */
  def loadPartition(
      name: TableIdentifier,
      loadPath: String,
      partition: TablePartitionSpec,
      isOverwrite: Boolean,
      holdDDLTime: Boolean,
      inheritTableSpecs: Boolean,
      isSkewedStoreAsSubdir: Boolean): Unit = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(name.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Some(db)))
    externalCatalog.loadPartition(db, table, loadPath, partition, isOverwrite, holdDDLTime,
      inheritTableSpecs, isSkewedStoreAsSubdir)
  }

  def defaultTablePath(tableIdent: TableIdentifier): String = {
    val dbName = formatDatabaseName(tableIdent.database.getOrElse(getCurrentDatabase))
    val dbLocation = getDatabaseMetadata(dbName).locationUri

    new Path(new Path(dbLocation), formatTableName(tableIdent.table)).toString
  }

  // -------------------------------------------------------------
  // | Methods that interact with temporary and metastore tables |
  // -------------------------------------------------------------

  /**
   * Create a temporary table.
   */
  def createTempView(
      name: String,
      tableDefinition: LogicalPlan,
      overrideIfExists: Boolean): Unit = synchronized {
    val table = formatTableName(name)
    if (tempTables.contains(table) && !overrideIfExists) {
      throw new TempTableAlreadyExistsException(name)
    }
    tempTables.put(table, tableDefinition)
  }

  /**
   * Rename a table.
   *
   * If a database is specified in `oldName`, this will rename the table in that database.
   * If no database is specified, this will first attempt to rename a temporary table with
   * the same name, then, if that does not exist, rename the table in the current database.
   *
   * This assumes the database specified in `oldName` matches the one specified in `newName`.
   */
  def renameTable(oldName: TableIdentifier, newName: TableIdentifier): Unit = synchronized {
    val db = formatDatabaseName(oldName.database.getOrElse(currentDb))
    requireDbExists(db)
    val newDb = formatDatabaseName(newName.database.getOrElse(currentDb))
    if (db != newDb) {
      throw new AnalysisException(
        s"RENAME TABLE source and destination databases do not match: '$db' != '$newDb'")
    }
    val oldTableName = formatTableName(oldName.table)
    val newTableName = formatTableName(newName.table)
    if (oldName.database.isDefined || !tempTables.contains(oldTableName)) {
      requireTableExists(TableIdentifier(oldTableName, Some(db)))
      requireTableNotExists(TableIdentifier(newTableName, Some(db)))
      externalCatalog.renameTable(db, oldTableName, newTableName)
    } else {
      if (newName.database.isDefined) {
        throw new AnalysisException(
          s"RENAME TEMPORARY TABLE from '$oldName' to '$newName': cannot specify database " +
            s"name '${newName.database.get}' in the destination table")
      }
      if (tempTables.contains(newTableName)) {
        throw new AnalysisException(
          s"RENAME TEMPORARY TABLE from '$oldName' to '$newName': destination table already exists")
      }
      val table = tempTables(oldTableName)
      tempTables.remove(oldTableName)
      tempTables.put(newTableName, table)
    }
  }

  /**
   * Drop a table.
   *
   * If a database is specified in `name`, this will drop the table from that database.
   * If no database is specified, this will first attempt to drop a temporary table with
   * the same name, then, if that does not exist, drop the table from the current database.
   */
  def dropTable(name: TableIdentifier, ignoreIfNotExists: Boolean): Unit = synchronized {
    val db = formatDatabaseName(name.database.getOrElse(currentDb))
    val table = formatTableName(name.table)
    if (name.database.isDefined || !tempTables.contains(table)) {
      requireDbExists(db)
      // When ignoreIfNotExists is false, no exception is issued when the table does not exist.
      // Instead, log it as an error message.
      if (tableExists(TableIdentifier(table, Option(db)))) {
        externalCatalog.dropTable(db, table, ignoreIfNotExists = true)
      } else if (!ignoreIfNotExists) {
        throw new NoSuchTableException(db = db, table = table)
      }
    } else {
      tempTables.remove(table)
    }
  }

  /**
   * Return a [[LogicalPlan]] that represents the given table.
   *
   * If a database is specified in `name`, this will return the table from that database.
   * If no database is specified, this will first attempt to return a temporary table with
   * the same name, then, if that does not exist, return the table from the current database.
   */
  def lookupRelation(name: TableIdentifier, alias: Option[String] = None): LogicalPlan = {
    synchronized {
      val db = formatDatabaseName(name.database.getOrElse(currentDb))
      val table = formatTableName(name.table)
      val relation =
        if (name.database.isDefined || !tempTables.contains(table)) {
          val metadata = externalCatalog.getTable(db, table)
          SimpleCatalogRelation(db, metadata, alias)
        } else {
          tempTables(table)
        }
      val qualifiedTable = SubqueryAlias(table, relation)
      // If an alias was specified by the lookup, wrap the plan in a subquery so that
      // attributes are properly qualified with this alias.
      alias.map(a => SubqueryAlias(a, qualifiedTable)).getOrElse(qualifiedTable)
    }
  }

  /**
   * Return whether a table with the specified name exists.
   *
   * Note: If a database is explicitly specified, then this will return whether the table
   * exists in that particular database instead. In that case, even if there is a temporary
   * table with the same name, we will return false if the specified database does not
   * contain the table.
   */
  def tableExists(name: TableIdentifier): Boolean = synchronized {
    val db = formatDatabaseName(name.database.getOrElse(currentDb))
    val table = formatTableName(name.table)
    if (name.database.isDefined || !tempTables.contains(table)) {
      externalCatalog.tableExists(db, table)
    } else {
      true // it's a temporary table
    }
  }

  /**
   * Return whether a table with the specified name is a temporary table.
   *
   * Note: The temporary table cache is checked only when database is not
   * explicitly specified.
   */
  def isTemporaryTable(name: TableIdentifier): Boolean = synchronized {
    name.database.isEmpty && tempTables.contains(formatTableName(name.table))
  }

  /**
   * List all tables in the specified database, including temporary tables.
   */
  def listTables(db: String): Seq[TableIdentifier] = listTables(db, "*")

  /**
   * List all matching tables in the specified database, including temporary tables.
   */
  def listTables(db: String, pattern: String): Seq[TableIdentifier] = {
    val dbName = formatDatabaseName(db)
    requireDbExists(dbName)
    val dbTables =
      externalCatalog.listTables(dbName, pattern).map { t => TableIdentifier(t, Some(dbName)) }
    synchronized {
      val _tempTables = StringUtils.filterPattern(tempTables.keys.toSeq, pattern)
        .map { t => TableIdentifier(t) }
      dbTables ++ _tempTables
    }
  }

  // TODO: It's strange that we have both refresh and invalidate here.

  /**
   * Refresh the cache entry for a metastore table, if any.
   */
  def refreshTable(name: TableIdentifier): Unit = { /* no-op */ }

  /**
   * Invalidate the cache entry for a metastore table, if any.
   */
  def invalidateTable(name: TableIdentifier): Unit = { /* no-op */ }

  /**
   * Drop all existing temporary tables.
   * For testing only.
   */
  def clearTempTables(): Unit = synchronized {
    tempTables.clear()
  }

  /**
   * Return a temporary table exactly as it was stored.
   * For testing only.
   */
  private[catalog] def getTempTable(name: String): Option[LogicalPlan] = synchronized {
    tempTables.get(name)
  }

  // ----------------------------------------------------------------------------
  // Partitions
  // ----------------------------------------------------------------------------
  // All methods in this category interact directly with the underlying catalog.
  // These methods are concerned with only metastore tables.
  // ----------------------------------------------------------------------------

  // TODO: We need to figure out how these methods interact with our data source
  // tables. For such tables, we do not store values of partitioning columns in
  // the metastore. For now, partition values of a data source table will be
  // automatically discovered when we load the table.

  /**
   * Create partitions in an existing table, assuming it exists.
   * If no database is specified, assume the table is in the current database.
   */
  def createPartitions(
      tableName: TableIdentifier,
      parts: Seq[CatalogTablePartition],
      ignoreIfExists: Boolean): Unit = {
    requireExactMatchedPartitionSpec(parts.map(_.spec), getTableMetadata(tableName))
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.createPartitions(db, table, parts, ignoreIfExists)
  }

  /**
   * Drop partitions from a table, assuming they exist.
   * If no database is specified, assume the table is in the current database.
   */
  def dropPartitions(
      tableName: TableIdentifier,
      specs: Seq[TablePartitionSpec],
      ignoreIfNotExists: Boolean): Unit = {
    requirePartialMatchedPartitionSpec(specs, getTableMetadata(tableName))
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.dropPartitions(db, table, specs, ignoreIfNotExists)
  }

  /**
   * Override the specs of one or many existing table partitions, assuming they exist.
   *
   * This assumes index i of `specs` corresponds to index i of `newSpecs`.
   * If no database is specified, assume the table is in the current database.
   */
  def renamePartitions(
      tableName: TableIdentifier,
      specs: Seq[TablePartitionSpec],
      newSpecs: Seq[TablePartitionSpec]): Unit = {
    val tableMetadata = getTableMetadata(tableName)
    requireExactMatchedPartitionSpec(specs, tableMetadata)
    requireExactMatchedPartitionSpec(newSpecs, tableMetadata)
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.renamePartitions(db, table, specs, newSpecs)
  }

  /**
   * Alter one or many table partitions whose specs that match those specified in `parts`,
   * assuming the partitions exist.
   *
   * If no database is specified, assume the table is in the current database.
   *
   * Note: If the underlying implementation does not support altering a certain field,
   * this becomes a no-op.
   */
  def alterPartitions(tableName: TableIdentifier, parts: Seq[CatalogTablePartition]): Unit = {
    requireExactMatchedPartitionSpec(parts.map(_.spec), getTableMetadata(tableName))
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.alterPartitions(db, table, parts)
  }

  /**
   * Retrieve the metadata of a table partition, assuming it exists.
   * If no database is specified, assume the table is in the current database.
   */
  def getPartition(tableName: TableIdentifier, spec: TablePartitionSpec): CatalogTablePartition = {
    requireExactMatchedPartitionSpec(Seq(spec), getTableMetadata(tableName))
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.getPartition(db, table, spec)
  }

  /**
   * List the metadata of all partitions that belong to the specified table, assuming it exists.
   *
   * A partial partition spec may optionally be provided to filter the partitions returned.
   * For instance, if there exist partitions (a='1', b='2'), (a='1', b='3') and (a='2', b='4'),
   * then a partial spec of (a='1') will return the first two only.
   */
  def listPartitions(
      tableName: TableIdentifier,
      partialSpec: Option[TablePartitionSpec] = None): Seq[CatalogTablePartition] = {
    val db = formatDatabaseName(tableName.database.getOrElse(getCurrentDatabase))
    val table = formatTableName(tableName.table)
    requireDbExists(db)
    requireTableExists(TableIdentifier(table, Option(db)))
    externalCatalog.listPartitions(db, table, partialSpec)
  }

  /**
   * Verify if the input partition spec exactly matches the existing defined partition spec
   * The columns must be the same but the orders could be different.
   */
  private def requireExactMatchedPartitionSpec(
      specs: Seq[TablePartitionSpec],
      table: CatalogTable): Unit = {
    val defined = table.partitionColumnNames.sorted
    specs.foreach { s =>
      if (s.keys.toSeq.sorted != defined) {
        throw new AnalysisException(
          s"Partition spec is invalid. The spec (${s.keys.mkString(", ")}) must match " +
            s"the partition spec (${table.partitionColumnNames.mkString(", ")}) defined in " +
            s"table '${table.identifier}'")
      }
    }
  }

  /**
   * Verify if the input partition spec partially matches the existing defined partition spec
   * That is, the columns of partition spec should be part of the defined partition spec.
   */
  private def requirePartialMatchedPartitionSpec(
      specs: Seq[TablePartitionSpec],
      table: CatalogTable): Unit = {
    val defined = table.partitionColumnNames
    specs.foreach { s =>
      if (!s.keys.forall(defined.contains)) {
        throw new AnalysisException(
          s"Partition spec is invalid. The spec (${s.keys.mkString(", ")}) must be contained " +
            s"within the partition spec (${table.partitionColumnNames.mkString(", ")}) defined " +
            s"in table '${table.identifier}'")
      }
    }
  }

  // ----------------------------------------------------------------------------
  // Functions
  // ----------------------------------------------------------------------------
  // There are two kinds of functions, temporary functions and metastore
  // functions (permanent UDFs). Temporary functions are isolated across
  // sessions. Metastore functions can be used across multiple sessions as
  // their metadata is persisted in the underlying catalog.
  // ----------------------------------------------------------------------------

  // -------------------------------------------------------
  // | Methods that interact with metastore functions only |
  // -------------------------------------------------------

  /**
   * Create a metastore function in the database specified in `funcDefinition`.
   * If no such database is specified, create it in the current database.
   */
  def createFunction(funcDefinition: CatalogFunction, ignoreIfExists: Boolean): Unit = {
    val db = formatDatabaseName(funcDefinition.identifier.database.getOrElse(getCurrentDatabase))
    requireDbExists(db)
    val identifier = FunctionIdentifier(funcDefinition.identifier.funcName, Some(db))
    val newFuncDefinition = funcDefinition.copy(identifier = identifier)
    if (!functionExists(identifier)) {
      externalCatalog.createFunction(db, newFuncDefinition)
    } else if (!ignoreIfExists) {
      throw new FunctionAlreadyExistsException(db = db, func = identifier.toString)
    }
  }

  /**
   * Drop a metastore function.
   * If no database is specified, assume the function is in the current database.
   */
  def dropFunction(name: FunctionIdentifier, ignoreIfNotExists: Boolean): Unit = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    requireDbExists(db)
    val identifier = name.copy(database = Some(db))
    if (functionExists(identifier)) {
      // TODO: registry should just take in FunctionIdentifier for type safety
      if (functionRegistry.functionExists(identifier.unquotedString)) {
        // If we have loaded this function into the FunctionRegistry,
        // also drop it from there.
        // For a permanent function, because we loaded it to the FunctionRegistry
        // when it's first used, we also need to drop it from the FunctionRegistry.
        functionRegistry.dropFunction(identifier.unquotedString)
      }
      externalCatalog.dropFunction(db, name.funcName)
    } else if (!ignoreIfNotExists) {
      throw new NoSuchFunctionException(db = db, func = identifier.toString)
    }
  }

  /**
   * Retrieve the metadata of a metastore function.
   *
   * If a database is specified in `name`, this will return the function in that database.
   * If no database is specified, this will return the function in the current database.
   */
  def getFunctionMetadata(name: FunctionIdentifier): CatalogFunction = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    requireDbExists(db)
    externalCatalog.getFunction(db, name.funcName)
  }

  /**
   * Check if the specified function exists.
   */
  def functionExists(name: FunctionIdentifier): Boolean = {
    val db = formatDatabaseName(name.database.getOrElse(getCurrentDatabase))
    requireDbExists(db)
    functionRegistry.functionExists(name.unquotedString) ||
      externalCatalog.functionExists(db, name.funcName)
  }

  // ----------------------------------------------------------------
  // | Methods that interact with temporary and metastore functions |
  // ----------------------------------------------------------------

  /**
   * Construct a [[FunctionBuilder]] based on the provided class that represents a function.
   *
   * This performs reflection to decide what type of [[Expression]] to return in the builder.
   */
  private[sql] def makeFunctionBuilder(name: String, functionClassName: String): FunctionBuilder = {
    // TODO: at least support UDAFs here
    throw new UnsupportedOperationException("Use sqlContext.udf.register(...) instead.")
  }

  /**
   * Loads resources such as JARs and Files for a function. Every resource is represented
   * by a tuple (resource type, resource uri).
   */
  def loadFunctionResources(resources: Seq[FunctionResource]): Unit = {
    resources.foreach(functionResourceLoader.loadResource)
  }

  /**
   * Create a temporary function.
   * This assumes no database is specified in `funcDefinition`.
   */
  def createTempFunction(
      name: String,
      info: ExpressionInfo,
      funcDefinition: FunctionBuilder,
      ignoreIfExists: Boolean): Unit = {
    if (functionRegistry.lookupFunctionBuilder(name).isDefined && !ignoreIfExists) {
      throw new TempFunctionAlreadyExistsException(name)
    }
    functionRegistry.registerFunction(name, info, funcDefinition)
  }

  /**
   * Drop a temporary function.
   */
  def dropTempFunction(name: String, ignoreIfNotExists: Boolean): Unit = {
    if (!functionRegistry.dropFunction(name) && !ignoreIfNotExists) {
      throw new NoSuchTempFunctionException(name)
    }
  }

  protected def failFunctionLookup(name: String): Nothing = {
    throw new NoSuchFunctionException(db = currentDb, func = name)
  }

  /**
   * Look up the [[ExpressionInfo]] associated with the specified function, assuming it exists.
   */
  private[spark] def lookupFunctionInfo(name: FunctionIdentifier): ExpressionInfo = synchronized {
    // TODO: just make function registry take in FunctionIdentifier instead of duplicating this
    val database = name.database.orElse(Some(currentDb)).map(formatDatabaseName)
    val qualifiedName = name.copy(database = database)
    functionRegistry.lookupFunction(name.funcName)
      .orElse(functionRegistry.lookupFunction(qualifiedName.unquotedString))
      .getOrElse {
        val db = qualifiedName.database.get
        requireDbExists(db)
        if (externalCatalog.functionExists(db, name.funcName)) {
          val metadata = externalCatalog.getFunction(db, name.funcName)
          new ExpressionInfo(metadata.className, qualifiedName.unquotedString)
        } else {
          failFunctionLookup(name.funcName)
        }
      }
  }

  /**
   * Return an [[Expression]] that represents the specified function, assuming it exists.
   *
   * For a temporary function or a permanent function that has been loaded,
   * this method will simply lookup the function through the
   * FunctionRegistry and create an expression based on the builder.
   *
   * For a permanent function that has not been loaded, we will first fetch its metadata
   * from the underlying external catalog. Then, we will load all resources associated
   * with this function (i.e. jars and files). Finally, we create a function builder
   * based on the function class and put the builder into the FunctionRegistry.
   * The name of this function in the FunctionRegistry will be `databaseName.functionName`.
   */
  def lookupFunction(
      name: FunctionIdentifier,
      children: Seq[Expression]): Expression = synchronized {
    // Note: the implementation of this function is a little bit convoluted.
    // We probably shouldn't use a single FunctionRegistry to register all three kinds of functions
    // (built-in, temp, and external).
    if (name.database.isEmpty && functionRegistry.functionExists(name.funcName)) {
      // This function has been already loaded into the function registry.
      return functionRegistry.lookupFunction(name.funcName, children)
    }

    // If the name itself is not qualified, add the current database to it.
    val database = name.database.orElse(Some(currentDb)).map(formatDatabaseName)
    val qualifiedName = name.copy(database = database)

    if (functionRegistry.functionExists(qualifiedName.unquotedString)) {
      // This function has been already loaded into the function registry.
      // Unlike the above block, we find this function by using the qualified name.
      return functionRegistry.lookupFunction(qualifiedName.unquotedString, children)
    }

    // The function has not been loaded to the function registry, which means
    // that the function is a permanent function (if it actually has been registered
    // in the metastore). We need to first put the function in the FunctionRegistry.
    // TODO: why not just check whether the function exists first?
    val catalogFunction = try {
      externalCatalog.getFunction(currentDb, name.funcName)
    } catch {
      case e: AnalysisException => failFunctionLookup(name.funcName)
      case e: NoSuchPermanentFunctionException => failFunctionLookup(name.funcName)
    }
    loadFunctionResources(catalogFunction.resources)
    // Please note that qualifiedName is provided by the user. However,
    // catalogFunction.identifier.unquotedString is returned by the underlying
    // catalog. So, it is possible that qualifiedName is not exactly the same as
    // catalogFunction.identifier.unquotedString (difference is on case-sensitivity).
    // At here, we preserve the input from the user.
    val info = new ExpressionInfo(catalogFunction.className, qualifiedName.unquotedString)
    val builder = makeFunctionBuilder(qualifiedName.unquotedString, catalogFunction.className)
    createTempFunction(qualifiedName.unquotedString, info, builder, ignoreIfExists = false)
    // Now, we need to create the Expression.
    functionRegistry.lookupFunction(qualifiedName.unquotedString, children)
  }

  /**
   * List all functions in the specified database, including temporary functions. This
   * returns the function identifier and the scope in which it was defined (system or user
   * defined).
   */
  def listFunctions(db: String): Seq[(FunctionIdentifier, String)] = listFunctions(db, "*")

  /**
   * List all matching functions in the specified database, including temporary functions. This
   * returns the function identifier and the scope in which it was defined (system or user
   * defined).
   */
  def listFunctions(db: String, pattern: String): Seq[(FunctionIdentifier, String)] = {
    val dbName = formatDatabaseName(db)
    requireDbExists(dbName)
    val dbFunctions = externalCatalog.listFunctions(dbName, pattern)
      .map { f => FunctionIdentifier(f, Some(dbName)) }
    val loadedFunctions = StringUtils.filterPattern(functionRegistry.listFunction(), pattern)
      .map { f => FunctionIdentifier(f) }
    val functions = dbFunctions ++ loadedFunctions
    functions.map {
      case f if FunctionRegistry.functionSet.contains(f.funcName) => (f, "SYSTEM")
      case f => (f, "USER")
    }
  }


  // -----------------
  // | Other methods |
  // -----------------

  /**
   * Drop all existing databases (except "default"), tables, partitions and functions,
   * and set the current database to "default".
   *
   * This is mainly used for tests.
   */
  private[sql] def reset(): Unit = synchronized {
    val default = "default"
    listDatabases().filter(_ != default).foreach { db =>
      dropDatabase(db, ignoreIfNotExists = false, cascade = true)
    }
    listTables(default).foreach { table =>
      dropTable(table, ignoreIfNotExists = false)
    }
    listFunctions(default).map(_._1).foreach { func =>
      if (func.database.isDefined) {
        dropFunction(func, ignoreIfNotExists = false)
      } else {
        dropTempFunction(func.funcName, ignoreIfNotExists = false)
      }
    }
    tempTables.clear()
    functionRegistry.clear()
    // restore built-in functions
    FunctionRegistry.builtin.listFunction().foreach { f =>
      val expressionInfo = FunctionRegistry.builtin.lookupFunction(f)
      val functionBuilder = FunctionRegistry.builtin.lookupFunctionBuilder(f)
      require(expressionInfo.isDefined, s"built-in function '$f' is missing expression info")
      require(functionBuilder.isDefined, s"built-in function '$f' is missing function builder")
      functionRegistry.registerFunction(f, expressionInfo.get, functionBuilder.get)
    }
    setCurrentDatabase(default)
  }

}
