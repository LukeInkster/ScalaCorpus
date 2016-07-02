---
title: Reading Custom Properties (Classification)
---

By default, the classification template reads 4 properties of a user entity: "attr0", "attr1", "attr2" and "plan". You can modify the [default DataSource](/templates/classification/dase/#data) to read to read your custom properties or different Entity Type.

In this example, we modify DataSource to read properties "featureA", "featureB", "featureC", "featureD" and "label" for entity type "item".

>> Note: you also need import events with these properties accordingly.

Modify the `readTraining()` in DataSource.scala:

- modify the `entityType` parameter
- modify the list of properties names in the `required` parameter
- modify how to create the `LabeledPoint` object using the entity properties

```scala
  def readTraining(sc: SparkContext): TrainingData = {
    ...
    val labeledPoints: RDD[LabeledPoint] = eventsDb.aggregateProperties(
      appId = dsp.appId,
      entityType = "item", // MODIFFIED HERE
      required = Some(List( // MODIFIED HERE
        "featureA", "featureB", "featureC", "featureD", "label")))(sc)
      // aggregateProperties() returns RDD pair of
      // entity ID and its aggregated properties
      .map { case (entityId, properties) =>
        try {
          // MODIFIED HERE
          LabeledPoint(properties.get[Double]("label"),
            Vectors.dense(Array(
              properties.get[Double]("featureA"),
              properties.get[Double]("featureB"),
              properties.get[Double]("featureC"),
              properties.get[Double]("featureD")
            ))
          )
        } catch {
          case e: Exception => {
            logger.error(s"Failed to get properties ${properties} of" +
              s" ${entityId}. Exception: ${e}.")
            throw e
          }
        }
      }
    ...
  }
```

Lastly, redefine the Query class parameters to take in four double values: featureA, featureB, featureC, and featureD. Now, to send a query, the field names must be changed accordingly:

```
$ curl -H "Content-Type: application/json" -d '{ "featureA":2, "featureB":0, "featureC":0, "featureD":0 }' http://localhost:8000/queries.json
```

That's it! Now your classifcation engine is using different properties as training data.
