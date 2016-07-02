package org.template.recommendation

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(user: String, num: Int, creationYear: Option[Int] = None)

case class PredictedResult(itemScores: Array[ItemScore])

// HOWTO: added movie creation year to predicted result.
case class ItemScore(item: String, score: Double, creationYear: Option[Int])

object RecommendationEngine extends IEngineFactory {
  def apply() =
    new Engine(classOf[DataSource],
      classOf[Preparator],
      Map("als" → classOf[ALSAlgorithm]),
      classOf[Serving])
}
