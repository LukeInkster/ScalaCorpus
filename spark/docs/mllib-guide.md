---
layout: global
title: MLlib
displayTitle: Machine Learning Library (MLlib) Guide
description: MLlib machine learning library overview for Spark SPARK_VERSION_SHORT
---

MLlib is Spark's machine learning (ML) library.
Its goal is to make practical machine learning scalable and easy.
It consists of common learning algorithms and utilities, including classification, regression,
clustering, collaborative filtering, dimensionality reduction, as well as lower-level optimization
primitives and higher-level pipeline APIs.

It divides into two packages:

* [`spark.mllib`](mllib-guide.html#data-types-algorithms-and-utilities) contains the original API
  built on top of [RDDs](programming-guide.html#resilient-distributed-datasets-rdds).
* [`spark.ml`](ml-guide.html) provides higher-level API
  built on top of [DataFrames](sql-programming-guide.html#dataframes) for constructing ML pipelines.

Using `spark.ml` is recommended because with DataFrames the API is more versatile and flexible.
But we will keep supporting `spark.mllib` along with the development of `spark.ml`.
Users should be comfortable using `spark.mllib` features and expect more features coming.
Developers should contribute new algorithms to `spark.ml` if they fit the ML pipeline concept well,
e.g., feature extractors and transformers.

We list major functionality from both below, with links to detailed guides.

# spark.mllib: data types, algorithms, and utilities

* [Data types](mllib-data-types.html)
* [Basic statistics](mllib-statistics.html)
  * [summary statistics](mllib-statistics.html#summary-statistics)
  * [correlations](mllib-statistics.html#correlations)
  * [stratified sampling](mllib-statistics.html#stratified-sampling)
  * [hypothesis testing](mllib-statistics.html#hypothesis-testing)
  * [streaming significance testing](mllib-statistics.html#streaming-significance-testing)
  * [random data generation](mllib-statistics.html#random-data-generation)
* [Classification and regression](mllib-classification-regression.html)
  * [linear models (SVMs, logistic regression, linear regression)](mllib-linear-methods.html)
  * [naive Bayes](mllib-naive-bayes.html)
  * [decision trees](mllib-decision-tree.html)
  * [ensembles of trees (Random Forests and Gradient-Boosted Trees)](mllib-ensembles.html)
  * [isotonic regression](mllib-isotonic-regression.html)
* [Collaborative filtering](mllib-collaborative-filtering.html)
  * [alternating least squares (ALS)](mllib-collaborative-filtering.html#collaborative-filtering)
* [Clustering](mllib-clustering.html)
  * [k-means](mllib-clustering.html#k-means)
  * [Gaussian mixture](mllib-clustering.html#gaussian-mixture)
  * [power iteration clustering (PIC)](mllib-clustering.html#power-iteration-clustering-pic)
  * [latent Dirichlet allocation (LDA)](mllib-clustering.html#latent-dirichlet-allocation-lda)
  * [bisecting k-means](mllib-clustering.html#bisecting-kmeans)
  * [streaming k-means](mllib-clustering.html#streaming-k-means)
* [Dimensionality reduction](mllib-dimensionality-reduction.html)
  * [singular value decomposition (SVD)](mllib-dimensionality-reduction.html#singular-value-decomposition-svd)
  * [principal component analysis (PCA)](mllib-dimensionality-reduction.html#principal-component-analysis-pca)
* [Feature extraction and transformation](mllib-feature-extraction.html)
* [Frequent pattern mining](mllib-frequent-pattern-mining.html)
  * [FP-growth](mllib-frequent-pattern-mining.html#fp-growth)
  * [association rules](mllib-frequent-pattern-mining.html#association-rules)
  * [PrefixSpan](mllib-frequent-pattern-mining.html#prefix-span)
* [Evaluation metrics](mllib-evaluation-metrics.html)
* [PMML model export](mllib-pmml-model-export.html)
* [Optimization (developer)](mllib-optimization.html)
  * [stochastic gradient descent](mllib-optimization.html#stochastic-gradient-descent-sgd)
  * [limited-memory BFGS (L-BFGS)](mllib-optimization.html#limited-memory-bfgs-l-bfgs)

# spark.ml: high-level APIs for ML pipelines

* [Overview: estimators, transformers and pipelines](ml-guide.html)
* [Extracting, transforming and selecting features](ml-features.html)
* [Classification and regression](ml-classification-regression.html)
* [Clustering](ml-clustering.html)
* [Collaborative filtering](ml-collaborative-filtering.html)
* [Advanced topics](ml-advanced.html)

Some techniques are not available yet in spark.ml, most notably dimensionality reduction 
Users can seamlessly combine the implementation of these techniques found in `spark.mllib` with the rest of the algorithms found in `spark.ml`.

# Dependencies

MLlib uses the linear algebra package [Breeze](http://www.scalanlp.org/), which depends on
[netlib-java](https://github.com/fommil/netlib-java) for optimised numerical processing.
If natives libraries[^1] are not available at runtime, you will see a warning message and a pure JVM
implementation will be used instead.

Due to licensing issues with runtime proprietary binaries, we do not include `netlib-java`'s native
proxies by default.
To configure `netlib-java` / Breeze to use system optimised binaries, include
`com.github.fommil.netlib:all:1.1.2` (or build Spark with `-Pnetlib-lgpl`) as a dependency of your
project and read the [netlib-java](https://github.com/fommil/netlib-java) documentation for your
platform's additional installation instructions.

To use MLlib in Python, you will need [NumPy](http://www.numpy.org) version 1.4 or newer.

[^1]: To learn more about the benefits and background of system optimised natives, you may wish to
    watch Sam Halliday's ScalaX talk on [High Performance Linear Algebra in Scala](http://fommil.github.io/scalax14/#/).

# Migration guide

MLlib is under active development.
The APIs marked `Experimental`/`DeveloperApi` may change in future releases,
and the migration guide below will explain all changes between releases.

## From 1.6 to 2.0

### Breaking changes

There were several breaking changes in Spark 2.0, which are outlined below.

**Linear algebra classes for DataFrame-based APIs**

Spark's linear algebra dependencies were moved to a new project, `mllib-local` 
(see [SPARK-13944](https://issues.apache.org/jira/browse/SPARK-13944)). 
As part of this change, the linear algebra classes were copied to a new package, `spark.ml.linalg`. 
The DataFrame-based APIs in `spark.ml` now depend on the `spark.ml.linalg` classes, 
leading to a few breaking changes, predominantly in various model classes 
(see [SPARK-14810](https://issues.apache.org/jira/browse/SPARK-14810) for a full list).

**Note:** the RDD-based APIs in `spark.mllib` continue to depend on the previous package `spark.mllib.linalg`.

_Converting vectors and matrices_

While most pipeline components support backward compatibility for loading, 
some existing `DataFrames` and pipelines in Spark versions prior to 2.0, that contain vector or matrix 
columns, may need to be migrated to the new `spark.ml` vector and matrix types. 
Utilities for converting `DataFrame` columns from `spark.mllib.linalg` to `spark.ml.linalg` types
(and vice versa) can be found in `spark.mllib.util.MLUtils`.

There are also utility methods available for converting single instances of 
vectors and matrices. Use the `asML` method on a `mllib.linalg.Vector` / `mllib.linalg.Matrix`
for converting to `ml.linalg` types, and 
`mllib.linalg.Vectors.fromML` / `mllib.linalg.Matrices.fromML` 
for converting to `mllib.linalg` types.

<div class="codetabs">
<div data-lang="scala"  markdown="1">

{% highlight scala %}
import org.apache.spark.mllib.util.MLUtils

// convert DataFrame columns
val convertedVecDF = MLUtils.convertVectorColumnsToML(vecDF)
val convertedMatrixDF = MLUtils.convertMatrixColumnsToML(matrixDF)
// convert a single vector or matrix
val mlVec: org.apache.spark.ml.linalg.Vector = mllibVec.asML
val mlMat: org.apache.spark.ml.linalg.Matrix = mllibMat.asML
{% endhighlight %}

Refer to the [`MLUtils` Scala docs](api/scala/index.html#org.apache.spark.mllib.util.MLUtils$) for further detail.
</div>

<div data-lang="java" markdown="1">

{% highlight java %}
import org.apache.spark.mllib.util.MLUtils;
import org.apache.spark.sql.Dataset;

// convert DataFrame columns
Dataset<Row> convertedVecDF = MLUtils.convertVectorColumnsToML(vecDF);
Dataset<Row> convertedMatrixDF = MLUtils.convertMatrixColumnsToML(matrixDF);
// convert a single vector or matrix
org.apache.spark.ml.linalg.Vector mlVec = mllibVec.asML();
org.apache.spark.ml.linalg.Matrix mlMat = mllibMat.asML();
{% endhighlight %}

Refer to the [`MLUtils` Java docs](api/java/org/apache/spark/mllib/util/MLUtils.html) for further detail.
</div>

<div data-lang="python"  markdown="1">

{% highlight python %}
from pyspark.mllib.util import MLUtils

# convert DataFrame columns
convertedVecDF = MLUtils.convertVectorColumnsToML(vecDF)
convertedMatrixDF = MLUtils.convertMatrixColumnsToML(matrixDF)
# convert a single vector or matrix
mlVec = mllibVec.asML()
mlMat = mllibMat.asML()
{% endhighlight %}

Refer to the [`MLUtils` Python docs](api/python/pyspark.mllib.html#pyspark.mllib.util.MLUtils) for further detail.
</div>
</div>

**Deprecated methods removed**

Several deprecated methods were removed in the `spark.mllib` and `spark.ml` packages:

* `setScoreCol` in `ml.evaluation.BinaryClassificationEvaluator`
* `weights` in `LinearRegression` and `LogisticRegression` in `spark.ml`
* `setMaxNumIterations` in `mllib.optimization.LBFGS` (marked as `DeveloperApi`)
* `treeReduce` and `treeAggregate` in `mllib.rdd.RDDFunctions` (these functions are available on `RDD`s directly, and were marked as `DeveloperApi`)
* `defaultStategy` in `mllib.tree.configuration.Strategy`
* `build` in `mllib.tree.Node`
* libsvm loaders for multiclass and load/save labeledData methods in `mllib.util.MLUtils`

A full list of breaking changes can be found at [SPARK-14810](https://issues.apache.org/jira/browse/SPARK-14810).

### Deprecations and changes of behavior

**Deprecations**

Deprecations in the `spark.mllib` and `spark.ml` packages include:

* [SPARK-14984](https://issues.apache.org/jira/browse/SPARK-14984):
 In `spark.ml.regression.LinearRegressionSummary`, the `model` field has been deprecated.
* [SPARK-13784](https://issues.apache.org/jira/browse/SPARK-13784):
 In `spark.ml.regression.RandomForestRegressionModel` and `spark.ml.classification.RandomForestClassificationModel`,
 the `numTrees` parameter has been deprecated in favor of `getNumTrees` method.
* [SPARK-13761](https://issues.apache.org/jira/browse/SPARK-13761):
 In `spark.ml.param.Params`, the `validateParams` method has been deprecated.
 We move all functionality in overridden methods to the corresponding `transformSchema`.
* [SPARK-14829](https://issues.apache.org/jira/browse/SPARK-14829):
 In `spark.mllib` package, `LinearRegressionWithSGD`, `LassoWithSGD`, `RidgeRegressionWithSGD` and `LogisticRegressionWithSGD` have been deprecated.
 We encourage users to use `spark.ml.regression.LinearRegresson` and `spark.ml.classification.LogisticRegresson`.
* [SPARK-14900](https://issues.apache.org/jira/browse/SPARK-14900):
 In `spark.mllib.evaluation.MulticlassMetrics`, the parameters `precision`, `recall` and `fMeasure` have been deprecated in favor of `accuracy`.
* [SPARK-15644](https://issues.apache.org/jira/browse/SPARK-15644):
 In `spark.ml.util.MLReader` and `spark.ml.util.MLWriter`, the `context` method has been deprecated in favor of `session`.
* In `spark.ml.feature.ChiSqSelectorModel`, the `setLabelCol` method has been deprecated since it was not used by `ChiSqSelectorModel`.

**Changes of behavior**

Changes of behavior in the `spark.mllib` and `spark.ml` packages include:

* [SPARK-7780](https://issues.apache.org/jira/browse/SPARK-7780):
 `spark.mllib.classification.LogisticRegressionWithLBFGS` directly calls `spark.ml.classification.LogisticRegresson` for binary classification now.
 This will introduce the following behavior changes for `spark.mllib.classification.LogisticRegressionWithLBFGS`:
    * The intercept will not be regularized when training binary classification model with L1/L2 Updater.
    * If users set without regularization, training with or without feature scaling will return the same solution by the same convergence rate.
* [SPARK-13429](https://issues.apache.org/jira/browse/SPARK-13429):
 In order to provide better and consistent result with `spark.ml.classification.LogisticRegresson`,
 the default value of `spark.mllib.classification.LogisticRegressionWithLBFGS`: `convergenceTol` has been changed from 1E-4 to 1E-6.
* [SPARK-12363](https://issues.apache.org/jira/browse/SPARK-12363):
 Fix a bug of `PowerIterationClustering` which will likely change its result.
* [SPARK-13048](https://issues.apache.org/jira/browse/SPARK-13048):
 `LDA` using the `EM` optimizer will keep the last checkpoint by default, if checkpointing is being used.
* [SPARK-12153](https://issues.apache.org/jira/browse/SPARK-12153):
 `Word2Vec` now respects sentence boundaries. Previously, it did not handle them correctly.
* [SPARK-10574](https://issues.apache.org/jira/browse/SPARK-10574):
 `HashingTF` uses `MurmurHash3` as default hash algorithm in both `spark.ml` and `spark.mllib`.
* [SPARK-14768](https://issues.apache.org/jira/browse/SPARK-14768):
 The `expectedType` argument for PySpark `Param` was removed.
* [SPARK-14931](https://issues.apache.org/jira/browse/SPARK-14931):
 Some default `Param` values, which were mismatched between pipelines in Scala and Python, have been changed.
* [SPARK-13600](https://issues.apache.org/jira/browse/SPARK-13600):
 `QuantileDiscretizer` now uses `spark.sql.DataFrameStatFunctions.approxQuantile` to find splits (previously used custom sampling logic).
 The output buckets will differ for same input data and params.

## Previous Spark versions

Earlier migration guides are archived [on this page](mllib-migration-guides.html).

---
