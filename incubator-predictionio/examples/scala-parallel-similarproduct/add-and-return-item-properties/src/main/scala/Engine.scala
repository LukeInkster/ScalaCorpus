package org.template.similarproduct

import io.prediction.controller.IEngineFactory
import io.prediction.controller.Engine

case class Query(
  items: List[String],
  num: Int,
  categories: Option[Set[String]],
  whiteList: Option[Set[String]],
  blackList: Option[Set[String]]
)

case class PredictedResult(
  itemScores: Array[ItemScore]
)

case class ItemScore(
  item: String,
  title: String,
  date: String,
  imdbUrl: String,
  score: Double
)

object SimilarProductEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[DataSource],
      classOf[Preparator],
      Map("als" -> classOf[ALSAlgorithm]),
      classOf[Serving])
  }
}