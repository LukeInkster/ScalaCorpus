package io.prediction.examples.java.recommendations.tutorial4;

import io.prediction.controller.java.JavaParams;

public class CollaborativeFilteringAlgorithmParams implements JavaParams {
  public double threshold;

  public CollaborativeFilteringAlgorithmParams(double threshold) {
    this.threshold = threshold;
  }
}
