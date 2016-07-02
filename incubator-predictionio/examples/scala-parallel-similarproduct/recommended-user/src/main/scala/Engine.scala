package org.template.recommendeduser

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(
  users: List[String],
  num: Int,
  whiteList: Option[Set[String]],
  blackList: Option[Set[String]]
)

case class PredictedResult(
  similarUserScores: Array[similarUserScore]
)

case class similarUserScore(
  user: String,
  score: Double
)

object RecommendedUserEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm]),
      classOf[Serving])
  }
}
