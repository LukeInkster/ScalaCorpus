package io.prediction.examples.java.regression;

import io.prediction.controller.java.LJavaServing;
import io.prediction.controller.java.EmptyParams;

import java.lang.Iterable;

public class Serving extends LJavaServing<EmptyParams, Double[], Double> {
  public Double serve(Double[] query, Iterable<Double> predictions) {
    int n = 0;
    double s = 0.0;
    for (Double d: predictions) {
      n += 1;
      s += d;
    }
    return s / n;
  }
}
