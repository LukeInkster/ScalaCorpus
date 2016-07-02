---
title: Text Classification Engine Tutorial
---

(Updated for Text Classification Template version 3.1)

## Introduction

In the real world, there are many applications that collect text as data. For example, spam detectors take email and header content to automatically determine what is or is not spam; applications can gague the general sentiment in a geographical area by analyzing Twitter data; and news articles can be automatically categorized based solely on the text content.There are a wide array of machine learning models you can use to create, or train, a predictive model to assign an incoming article, or query, to an existing category. Before you can use these techniques you must first transform the text data (in this case the set of news articles) into numeric vectors, or feature vectors, that can be used to train your model.

The purpose of this tutorial is to illustrate how you can go about doing this using PredictionIO's platform. The advantages of using this platform include: a dynamic engine that responds to queries in real-time; [separation of concerns](http://en.wikipedia.org/wiki/Separation_of_concerns), which offers code re-use and maintainability, and distributed computing capabilities for scalability and efficiency. Moreover, it is easy to incorporate non-trivial data modeling tasks into the DASE architecture allowing Data Scientists to focus on tasks related to modeling. This tutorial will exemplify some of these ideas by guiding you through PredictionIO's [text classification template(http://templates.prediction.io/PredictionIO/template-scala-parallel-textclassification/).



## Prerequisites

Before getting started, please make sure that you have the latest version of PredictionIO [installed](https://docs.prediction.io/install/). We emphasize here that this is an engine template written in **Scala** and can be more generally thought of as an SBT project containing all the necessary components.

You should also download the engine template named Text Classification Engine that accompanies this tutorial by cloning the template repository:

```
pio template get PredictionIO/template-scala-parallel-textclassification < Your new engine directory >
```


## Engine Overview

The engine follows the DASE architecture which we briefly review here. As a user, you are tasked with collecting data for your web or application, and importing it into PredictionIO's Event Server. Once the data is in the server, it  can be read and processed by the engine via the Data Source and Preparation components, respectively. The Algorithm component uses the processed, or prepared, data to train a set of predictive models. Once you have trained these models, you are ready to deploy your engine and respond to real-time queries via the Serving component which combines the results from different fitted models. The Evaluation component is used to compute an appropriate metric to test the performance of a fitted model, as well as aid in the tuning of model hyper parameters.

This engine template is meant to handle text classification which means you will be working with text data. This means that a query, or newly observed documents, will be of the form:

`{text : String}`.

In the running example, a query would be an incoming news article. Once the engine is deployed it can process the query, and then return a Predicted Result of the form

`{category : String, confidence : Double}`.

Here category is the model's class assignment for this new text document (i.e. the best guess for this article's categorization), and confidence, a value between 0 and 1 representing your confidence in the category prediction (0 meaning you have no confidence in the prediction). The Actual Result is of the form

`{category : String}`.

This is used in the evaluation stage when estimating the performance of your predictive model (how well does the model predict categories). Please refer to the [following tutorial](https://docs.prediction.io/customize/) for a more detailed explanation of how your engine will interact with your web application, as well as an in depth-overview of DASE.


## Quick Start

This is a quick start guide in case you want to start using the engine right away. Sample email data for spam classification will be used. For more detailed information, read the subsequent sections.


### 1. Create a new application.

After the application is created, you will be given an access key and application ID for the application.

```
$ pio app new MyTextApp
```

### 2. Import the tutorial data.

There are three different data sets available, each giving a different use case for this engine. Please refer to the **Data Source: Reading Event Data** section to see how to appropriate modify the `DataSource` class for use with each respective data set. The default data set is an e-mail spam data set.

These data sets have already been processed and are ready for [batch import](/datacollection/batchimport/). Replace `***` with your actual application ID.

```
$ pio import --appid *** --input data/stopwords.json

$ pio import --appid *** --input data/emails.json
```

### 3. Set the engine parameters in the file `engine.json`.

The default settings are shown below. By default, it uses the algorithm name "lr" which is logstic regression. Please see later section for more detailed explanation of engine.json setting.

Make sure the "appName" is same as the app you created in step1.


```
{
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.template.textclassification.TextClassificationEngine",
  "datasource": {
    "params": {
      "appName": "MyTextApp"
    }
  },
  "preparator": {
    "params": {
      "nGram": 1,
      "numFeatures": 500,
      "SPPMI": false
    }
  },
  "algorithms": [
    {
      "name": "lr",
      "params": {
        "regParam": 0.00000005
      }
    }
  ]
}

```

### 4. Build your engine.

```
$ pio build --verbose
```

This command should take few minutes for the first time; all subsequent builds should be less than a minute. You can also run it without `--verbose` if you don't want to see all the log messages.

Upon successful build, you should see a console message similar to the following

```
[INFO] [RegisterEngine$] Registering engine 6wxDy2hxLbvaMJra927ahFdQHDIVXeQz 266bae678c570dee58154b2338cef7aa1646e0d3
[INFO] [Console$] Your engine is ready for training.
```

### 5.a. Train your model and deploy.

```
$ pio train
```

When your engine is trained successfully, you should see a console message similar to the following.

```
[INFO] [CoreWorkflow$] Training completed successfully.
```

Now your engine is ready to deploy. Run:

```
$ pio deploy
```

When the engine is deployed successfully and running, you should see a console message similar to the following:

```
[INFO] [HttpListener] Bound to /0.0.0.0:8000
[INFO] [MasterActor] Engine is deployed and running. Engine API is live at http://0.0.0.0:8000.
```

Now you can send query to the engine. Open another terminal and send the following http request to the deployed engine:

```
$ curl -H "Content-Type: application/json" -d '{ "text":"I like speed and fast motorcycles." }' http://localhost:8000/queries.json
```

you should see following outputs returned by the engine:

```
{"category":"not spam","confidence":0.852619510921587}
```

Try another query:

```
$ curl -H "Content-Type: application/json" -d '{ "text":"Earn extra cash!" }' http://localhost:8000/queries.json
```

you should see following outputs returned by the engine:

```
{"category":"spam","confidence":0.5268770133242983}
```


### 5.b.Evaluate your training model and tune parameters.

```
$ pio eval org.template.textclassification.AccuracyEvaluation org.template.textclassification.EngineParamsList
```

**Note:** Training and evaluation stages are generally different stages of engine development. Evaluation is there to help you choose the best [algorithm parameters](/evaluation/paramtuning/) to use for training an engine that is to be deployed as a web service.

Depending on your needs, in steps (5.x.) above, you can configure your Spark settings by typing a command of the form:

```
$ pio command command_parameters -- --master url --driver-memory {0}G --executor-memory {1}G --conf spark.akka.framesize={2} --total_executor_cores {3}
```

Only the latter commands are listed as these are some of the more commonly modified values. See the [Spark documentation](https://spark.apache.org/docs/latest/spark-standalone.html) and the [PredictionIO FAQ's](https://docs.prediction.io/resources/faq/) for more information.

**Note:** We recommend you set your driver memory to `1G` or `2G` as the data size when dealing with text can be very large.


# Detailed Explanation of DASE

## Importing Data

In the quick start, email spam classification is used. This template can easily be modified for other types text classification.

If you want to import different sets of data, follow the Quick Start instructions to import data from different files. Make sure that the Data Source is modified accordingly to match the `event`, `entityType`, and `properties` fields set for the specific dataset. The following section explains this in more detail.

## Data Source: Reading Event Data

Now that the data has been imported into PredictionIO's Event Server, it needs to be read from storage to be used by the engine. This is precisely what the DataSource engine component is for, which is implemented in the template script `DataSource.scala`. The class `Observation` serves as a wrapper for storing the information about a news document needed to train a model. The attribute label refers to the label of the category a document belongs to, and text, stores the actual document content as a string. The class TrainingData is used to store an RDD of Observation objects along with the set of stop words.

The class `DataSourceParams` is used to specify the parameters needed to read and prepare the data for processing. This class is initialized with two parameters `appName` and `evalK`. The first parameter specifies your application name (i.e. MyTextApp), which is needed so that the DataSource component knows where to pull the event data from. The second parameter is used for model evaluation and specifies the number of folds to use in [cross-validation](http://en.wikipedia.org/wiki/Cross-validation_%28statistics%29) when estimating a model performance metric.

The final and most important ingredient is the DataSource class. This is initialized with its corresponding parameter class, and extends `PDataSource`. This **must** implement the method `readTraining` which returns an instance of type TrainingData. This method completely relies on the defined private methods readEventData and readStopWords. Both of these functions read data observations as Event instances, create an RDD containing these events and finally transforms the RDD of events into an object of the appropriate type as seen below:

```scala
...
private def readEventData(sc: SparkContext) : RDD[Observation] = {
    //Get RDD of Events.
    PEventStore.find(
      appName = dsp.appName,
      entityType = Some("content"), // specify data entity type
      eventNames = Some(List("e-mail")) // specify data event name

      // Convert collected RDD of events to and RDD of Observation
      // objects.
    )(sc).map(e => {
      val label : String = e.properties.get[String]("label")
      Observation(
        if (label == "spam") 1.0 else 0.0,
        e.properties.get[String]("text"),
        label
      )
    }).cache
  }

  // Helper function used to store stop words from
  // event server.
  private def readStopWords(sc : SparkContext) : Set[String] = {
    PEventStore.find(
      appName = dsp.appName,
      entityType = Some("resource"),
      eventNames = Some(List("stopwords"))

    //Convert collected RDD of strings to a string set.
    )(sc)
      .map(e => e.properties.get[String]("word"))
      .collect
      .toSet
  }
...
```

Note that `readEventData` and `readStopWords` use different entity types and event names, but use the same application name. This is because the sample import script imports two different data types, documents and stop words. These field distinctions are required for distinguishing between the two. The method `readEval` is used to prepare the different cross-validation folds needed for evaluating your model and tuning hyper parameters.

Now, the default dataset used for training is contained in the file `data/emails.json` and contains a set of e-mail spam data. If we want to switch over to one of the other data sets we must make sure that the `eventNames` and `entityType` fields are changed accordingly.

In the data/ directory, you will find different sets of data files for different types of text classifcaiton application. The following show one observation from each of the provided data files:

- `emails.json`:

```
{"eventTime": "2015-06-08T16:45:00.590+0000", "entityId": 1, "properties": {"text": "Subject: dobmeos with hgh my energy level has gone up ! stukm\nintroducing\ndoctor - formulated\nhgh\nhuman growth hormone - also called hgh\nis referred to in medical science as the master hormone . it is very plentiful\nwhen we are young , but near the age of twenty - one our bodies begin to produce\nless of it . by the time we are forty nearly everyone is deficient in hgh ,\nand at eighty our production has normally diminished at least 90 - 95 % .\nadvantages of hgh :\n- increased muscle strength\n- loss in body fat\n- increased bone density\n- lower blood pressure\n- quickens wound healing\n- reduces cellulite\n- improved vision\n- wrinkle disappearance\n- increased skin thickness texture\n- increased energy levels\n- improved sleep and emotional stability\n- improved memory and mental alertness\n- increased sexual potency\n- resistance to common illness\n- strengthened heart muscle\n- controlled cholesterol\n- controlled mood swings\n- new hair growth and color restore\nread\nmore at this website\nunsubscribe\n", "label": "spam"}, "event": "e-mail", "entityType": "content"}

```

- `20newsgroups.json`:

```
{"entityType": "source", "eventTime": "2015-06-08T18:01:55.003+0000", "event": "documents", "entityId": 1, "properties": {"category": "sci.crypt", "text": "From: rj@ri.cadre.com (Rob deFriesse)\nSubject: Can DES code be shipped to Canada?\nArticle-I.D.: fripp.1993Apr22.125402.27561\nReply-To: rj@ri.cadre.com\nOrganization: Cadre Technologies Inc.\nLines: 13\nNntp-Posting-Host: 192.9.200.19\n\nSomeone in Canada asked me to send him some public domain DES file\nencryption code I have.  Is it legal for me to send it?\n\nThanx.\n--\nEschew Obfuscation\n\nRob deFriesse                    Mail:  rj@ri.cadre.com\nCadre Technologies Inc.          Phone:  (401) 351-5950\n222 Richmond St.                 Fax:    (401) 351-7380\nProvidence, RI  02903\n\nI don't speak for my employer.\n", "label": 11.0}}
```

- `sentimentanalysis.json`:

```
{"eventTime": "2015-06-08T16:58:14.278+0000", "entityId": 23714, "entityType": "source", "properties": {"phrase": "Tosca 's intoxicating ardor", "sentiment": 3}, "event": "phrases"}
```

Now, note that the `entityType`, `event`, and `properties`  fields for the `20newsgroups.json` dataset differ from the default `emails.json` set. Default DataSource implementation is to read from `email.json` data set. If you want to use others such as newsgroups data set, the engine's Data Source component must be modified accordingly. To do this, you need only modify the method `readEventData` as follows:

### Modify DataSource to Read `20newsgroups.json`

```scala
private def readEventData(sc: SparkContext) : RDD[Observation] = {
    //Get RDD of Events.
    PEventStore.find(
      appName = dsp.appName,
      entityType = Some("source"), // specify data entity type
      eventNames = Some(List("documents")) // specify data event name

      // Convert collected RDD of events to and RDD of Observation
      // objects.
    )(sc).map(e => {

      Observation(
        e.properties.get[Double]("label"),
        e.properties.get[String]("text"),
        e.properties.get[String]("category")
      )
    }).cache
  }
```

### Modify DataSource to Read `sentimentanalysis.json`

```scala
private def readEventData(sc: SparkContext) : RDD[Observation] = {
    //Get RDD of Events.
    PEventStore.find(
      appName = dsp.appName,
      entityType = Some("source"), // specify data entity type
      eventNames = Some(List("phrases")) // specify data event name

      // Convert collected RDD of events to and RDD of Observation
      // objects.
    )(sc).map(e => {
      val label = e.properties.get[Double]("sentiment")

      Observation(
        label,
        e.properties.get[String]("phrase"),
        label.toString
      )
    }).cache
  }
```

Note that `event` field in the json file refers to the `eventNames` field in the `readEventData` method. When using this engine with a custom data set, you need to make sure that the respective json fields match with the corresponding fields in the DataSource component. We have included a data sanity check with this engine component that lets you know if your data is actually being read in. If you have 0 observations being read, you should see the following output when your training process performs the Training Data sanity check:

`Data set is empty, make sure event fields match imported data.`

This data sanity check is a PredictionIO feature available for your `TrainingData` and `PreparedData` classes. The following code block demonstrates how the sanity check is implemented:

```scala
class TrainingData(
  val data : RDD[Observation],
  val stopWords : Set[String]
) extends Serializable with SanityCheck {

  // Sanity check to make sure your data is being fed in correctly.

  def sanityCheck {
    try {
      val obs : Array[Double] = data.takeSample(false, 5).map(_.label)

      println()
      (0 until 5).foreach(
        k => println("Observation " + (k + 1) +" label: " + obs(k))
      )
      println()
    } catch {
      case (e : ArrayIndexOutOfBoundsException) => {
        println()
        println("Data set is empty, make sure event fields match imported data.")
        println()
      }
    }

  }

}
```

## Preparator : Data Processing With DASE

Recall that the Preparator stage is used for doing any prior data processing needed to fit a predictive model. In line with the separation of concerns, the Data Model implementation, PreparedData, is built to do the heavy lifting needed for this data processing. The Preparator must simply implement the prepare method which outputs an object of type PreparedData. This requires you to specify two n-gram window components, and two inverse i.d.f. window components (these terms will be defined in the following section). Therefore a custom class of parameters for the Preparator component, PreparatorParams, must be incorporated. The code defining the full Preparator component is given below:

```scala
// 1. Initialize Preparator parameters. Recall that for our data
// representation we are only required to input the n-gram window
// components.

case class PreparatorParams(
  nGram: Int,
  numFeatures: Int = 5000,
  SPPMI: Boolean
) extends Params



// 2. Initialize your Preparator class.

class Preparator(pp: PreparatorParams) extends PPreparator[TrainingData, PreparedData] {

  // Prepare your training data.
  def prepare(sc : SparkContext, td: TrainingData): PreparedData = {
    new PreparedData(td, pp.nGram)
  }
}

```

The simplicity of this stage implementation truly exemplifies one of the benefits of using the PredictionIO platform. For developers, it is easy to incorporate different classes and tools into the DASE framework so that the process of creating an engine is greatly simplified which helps increase your productivity. For data scientists, the load of implementation details you need to worry about is minimized so that you can focus on what is important to you: training a good predictive model.

The following subsection explains the class PreparedData, which actually handles the transformation of text documents to feature vectors.

### PreparedData: Text Vectorization and Feature Reduction

The Scala class PreparedData which takes the parameters td, nGram, where td is an object of class TrainingData. The other parameter specifies the n-gram parametrization which will be described shortly.

It will be easier to explain the preparation process with an example, so consider the document \\(d\\):

`"Hello, my name is Marco."`

The first thing you need to do is break up \\(d\\) into an array of "allowed tokens." You can think of a token as a terminating sequence of characters that exist in a document (think of a word in a sentence). For example, the list of tokens that appear in \\(d\\) is:

```scala
val A = Array("Hello", ",", "my",  "name", "is", "Marco", ".")
```

Recall that a set of stop words was also imported in the previous sections. This set of stop words contains all the words (or tokens) that you do not want to include once documents are tokenized. Those tokens that appear in \\(d\\) and are not contained in the set of stop words will be called allowed tokens. So, if the set of stop words is `{"my", "is"}`, then the list of allowed tokens appearing in \\(d\\) is:

```scala
val A = Array("Hello", ",",  "name", "Marco", ".")
```

The next step in the data representation is to take the array of allowed tokens and extract a set of n-grams and a corresponding value indicating the number of times a given n-gram appears. The set of n-grams for n equal to 1 and 2 in the running example is the set of elements of the form `[A(`\\(i\\)`)]` and `[A(`\\(j\\)`), A(`\\(j + 1\\)`)]`, respectively. In the general case, the set of n-grams extracted from an array of allowed tokens `A` will be of the form `[A(`\\(i\\)`), A(`\\(i + 1\\)`), ..., A(`\\(i + n - 1\\)`)]` for \\(i = 0, 1, 2, ...,\\) `A.size` \\(- n\\). You can set `n` with the `nGram` parameter option in your `PreparatorParams`.

We use MLLib's `HashingTF` class to implement the conversion from text to term frequency vectors, and can be seen in the following method of the class `PreparedData`:

```scala
...
   // 1. Hashing function: Text -> term frequency vector.

  private val hasher = new HashingTF()

  private def hashTF (text : String) : Vector = {
    val newList : Array[String] = text.split(" ")
    .sliding(nGram)
    .map(_.mkString)
    .toArray

    hasher.transform(newList)
  }

  // 2. Term frequency vector -> t.f.-i.d.f. vector.

  val idf : IDFModel = new IDF().fit(td.data.map(e => hashTF(e.text)))
...
```

The next step is, once all of the observations have been hashed, to collect all n-grams and compute their corresponding [t.f.-i.d.f. value](http://en.wikipedia.org/wiki/Tf%E2%80%93idf). The t.f.-i.d.f. transformation is defined for n-grams, and helps to give less weight to those n-grams that appear with high frequency across all documents, and vice versa. This helps to leverage the predictive power of those words that appear rarely, but can make a big difference in the categorization of a given text document. This is implemented using MLLib's `IDF` and `IDFModel` classes:

```scala
// 2. Term frequency vector -> t.f.-i.d.f. vector.

  val idf : IDFModel = new IDF().fit(td.data.map(e => hashTF(e.text)))
```


The last two functions that will be mentioned are the methods you will actually use for the data transformation. The method transform takes a document and outputs a sparse vector (MLLib implementation). The transformData method simply transforms the TrainingData input (a corpus of documents) into a set of vectors that can now be used for training. The method transform is used both to transform the training data and future queries.

```scala
...
// 3. Document Transformer: text => tf-idf vector.

  def transform(text : String): Vector = {
    // Map(n-gram -> document tf)
    idf.transform(hashTF(text))
  }


  // 4. Data Transformer: RDD[documents] => RDD[LabeledPoints]

  val transformedData: RDD[(LabeledPoint)] = {
    td.data.map(e => LabeledPoint(e.label, transform(e.text)))
  }
```

The last and final object implemented in this class simply creates a Map with keys being class labels and values, the corresponding category.

```scala
 // 5. Finally extract category map, associating label to category.
  val categoryMap = td.data.map(e => (e.label, e.category)).collectAsMap
```


## Algorithm Component

The algorithm components in this engine, `NBAlgorithm` and `LRAlgorithm`, actually follows a very general form. Firstly, a parameter class must again be initialized to feed in the corresponding Algorithm model parameters. For example, NBAlgorithm incorporates NBAlgorithmParams which holds the appropriate additive smoothing parameter lambda for the Naive Bayes model.


The main class of interest in this component is the class that extends [P2LAlgorithm](https://docs.prediction.io/api/current/#io.prediction.controller.P2LAlgorithm). This class must implement a method named train which will output your predictive model (as a concrete object, this will be implemented via a Scala  class). It must also implement a predict method that transforms a query to an appropriate feature vector, and uses this to predict with the fitted model. The vectorization function is implemented by a PreparedData object, and the categorization (prediction) is handled by an instance of the NBModel implementation. Again, this demonstrates the facility with which different models can be incorporated into PredictionIO's DASE architecture.

The model class itself will be discussed in the following section, however, turn your attention to the TextManipulationEngine object defined in the script `Engine.scala`. You can see here that the engine is initialized by specifying the DataSource, Preparator, and Serving classes, as well as a Map of algorithm names to Algorithm classes. This tells the engine which algorithms to run. In practice, you can have as many statistical learning models as you'd like, you simply have to implement a new algorithm component to do this. However, this general design form will persist, and the main meat of the work should be in the implementation of your model class.

The following subsection will go over our Naive Bayes implementation in NBModel.


### Naive Bayes Classification

This Training Model class only uses the Multinomial Naive Bayes [implementation](https://spark.apache.org/docs/latest/mllib-naive-bayes.html) found in the Spark MLLib library. However, recall that the predicted results required in the specifications listed in the overview are of the form:


`{category: String, confidence: Double}`.

The confidence value should really be interpreted as the probability that a document belongs to a category given its vectorized form. Note that MLLib's Naive Bayes model has the class members pi (\\(\pi\\)), and theta (\\(\theta\\)). \\(\pi\\) is a vector of log prior class probabilities, which shows your prior beliefs regarding the probability that an arbitrary document belongs in a category. \\(\theta\\) is a C \\(\times\\) D matrix, where C is the number of classes, and D, the number of features, giving the log probabilities that parametrize the Multinomial likelihood model assumed for each class. The multinomial model is easiest to think about as a problem of randomly throwing balls into bins, where the ball lands in each bin with a certain probability. The model treats each n-gram as a bin, and the corresponding t.f.-i.d.f. value as the number of balls thrown into it. The likelihood is the probability of observing a (vectorized) document given that it comes from a particular class.

Now, letting \\(\mathbf{x}\\) be a vectorized text document, then it can be shown that the vector

$$
\frac{\exp\left(\pi + \theta\mathbf{x}\right)}{\left|\left|\exp\left(\pi + \theta\mathbf{x}\right)\right|\right|}
$$

is a vector with C components that represent the posterior class membership probabilities for the document given \\(\mathbf{x}\\). That is, the update belief regarding what category this document belongs to after observing its vectorized form. This is the motivation behind defining the class NBModel which uses Spark MLLib's NaiveBayesModel, but implements a separate prediction method.

The private methods innerProduct and getScores are implemented to do the matrix computation above.

```scala
...
 // 2. Set up linear algebra framework.

  private def innerProduct (x : Array[Double], y : Array[Double]) : Double = {
    x.zip(y).map(e => e._1 * e._2).sum
  }

  val normalize = (u: Array[Double]) => {
    val uSum = u.sum

    u.map(e => e / uSum)
  }



  // 3. Given a document string, return a vector of corresponding
  // class membership probabilities.

  private def getScores(doc: String): Array[Double] = {
    // Helper function used to normalize probability scores.
    // Returns an object of type Array[Double]

    // Vectorize query,
    val x: Vector = pd.transform(doc)

    normalize(
      nb.pi
      .zip(nb.theta)
      .map(
      e => exp(innerProduct(e._2, x.toArray) + e._1))
    )
  }
...
```


Once you have a vector of class probabilities, you can classify the text document to the category with highest posterior probability, and, finally, return both the category as well as the probability of belonging to that category (i.e. the confidence in the prediction) given the observed data. This is implemented in the method predict.

```scala
...
  // 4. Implement predict method for our model using
  // the prediction rule given in tutorial.

  def predict(doc : String) : PredictedResult = {
    val x: Array[Double] = getScores(doc)
    val y: (Double, Double) = (nb.labels zip x).maxBy(_._2)
    new PredictedResult(pd.categoryMap.getOrElse(y._1, ""), y._2)
  }
```

### Logistic Regression Classification

To use the alternative multinomial logistic regression algorithm change your `engine.json` as follows:

```json
  {
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.template.textclassification.TextClassificationEngine",
  "datasource": {
    "params": {
      "appName": "MyTextApp"
    }
  },
  "preparator": {
    "params": {
      "nGram": 2
    }
  },
  "algorithms": [
    {
      "name": "regParam",
      "params": {
        "regParam": 0.1
      }
    }
  ]
}
```


## Serving: Delivering the Final Prediction

The serving component is the final stage in the engine, and in a sense, the most important. This is the final stage in which you combine the results obtained from the different models you choose to run. The Serving class extends the [LServing](https://docs.prediction.io/api/current/#io.prediction.controller.LServing) class which must implement a method called serve. This takes a query and an associated sequence of predicted results, which contains the predicted results from the different algorithms that are implemented in your engine, and combines the results to yield a final prediction.  It is this final prediction that you will receive after sending a query.

For example, you could choose to slightly modify the implementation to return class probabilities coming from a mixture of model estimates for class probabilities, or any other technique you could conceive for combining your results. The default engine setting has this set to yield the label from the model predicting with greater confidence.



## Evaluation: Model Assessment and Selection

 A predictive model needs to be evaluated to see how it will generalize to future observations. PredictionIO uses cross-validation to perform model performance metric estimates needed to assess your particular choice of model. The script `Evaluation.scala` available with the engine template exemplifies what a usual evaluator setup will look like. First, you must define an appropriate metric. In the engine template, since the topic is text classification, the default metric implemented is category accuracy.

 Second you must define an evaluation object (i.e. extends the class [Evaluation](https://docs.prediction.io/api/current/#io.prediction.controller.Evaluation)).
Here, you must specify the actual engine and metric components that are to be used for the evaluation. In the engine template, the specified engine is the TextManipulationEngine object, and metric, Accuracy. Lastly, you must specify the parameter values that you want to test in the cross validation. You see in the following block of code:

```scala
object EngineParamsList extends EngineParamsGenerator {

  // Set data source and preparator parameters.
  private[this] val baseEP = EngineParams(
    dataSourceParams = DataSourceParams(appName = "marco-MyTextApp", evalK = Some(5)),
    preparatorParams = PreparatorParams(nMin = 1, nMax = 2)
  )

  // Set the algorithm params for which we will assess an accuracy score.
  engineParamsList = Seq(
    baseEP.copy(algorithmParamsList = Seq(("nb", NBAlgorithmParams(0.5)))),
    baseEP.copy(algorithmParamsList = Seq(("nb", NBAlgorithmParams(1.5)))),
    baseEP.copy(algorithmParamsList = Seq(("nb", NBAlgorithmParams(5))))
  )
```


## Engine Deployment

Once an engine is ready for deployment it can interact with your web application in real-time. This section will cover how to send and receive queries from your engine, gather more data, and re-training your model with the newly gathered data.

### Sending Queries

Recall that one of the greatest advantages of using the PredictionIO platform is that once your engine is deployed, you can respond to queries in real-time. Recall that our queries are of the form

`{"text" : "..."}`.

To actually send a query you can use our REST API by typing in the following shell command:

```
curl -H "Content-Type: application/json" -d '{ "text":"I like speed and fast motorcycles." }' http://localhost:8000/queries.json
```

There are a number of [SDK's](https://github.com/PredictionIO) you can use to send your queries and obtain a response. Recall that our predicted response is of the form

```
{"category" : "class", "confidence" : 1.0}
```

which is what you should see upon inputting the latter command for querying.

### Gathering More Data and Retraining Your Model

The importing data section that is included in this tutorial uses a sample data set for illustration purposes, and uses the PredictionIO Python SDK to import the data. However, there are a variety of ways that you can [import](https://docs.prediction.io/datacollection/eventapi/) your collected data (via REST or other SDKs).


As you continue to collect your data, it is quite easy to retrain your model once you actually import your data into the Event Server. You simply repeat the steps listed in the Quick Start guide. We re-list them here again:


**1.** Build your engine.

```
$ pio build
```

**2.a.** Evaluate your training model and tune parameters.

```
$ pio eval org.template.textclassification.AccuracyEvaluation org.template.textclassification.EngineParamsList
```

**2.b.** Train your model and deploy.

```
$ pio train
$ pio deploy
```
