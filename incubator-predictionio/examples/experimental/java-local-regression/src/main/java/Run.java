package io.prediction.examples.java.regression;

import io.prediction.controller.java.EmptyParams;
import io.prediction.controller.java.IJavaEngineFactory;
import io.prediction.controller.java.JavaParams;
import io.prediction.controller.java.JavaEngine;
import io.prediction.controller.java.JavaEngineBuilder;
import io.prediction.controller.java.JavaEngineParams;
import io.prediction.controller.java.JavaEngineParamsBuilder;
import io.prediction.controller.java.LJavaAlgorithm;
import io.prediction.controller.java.JavaWorkflow;
import io.prediction.controller.java.WorkflowParamsBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import scala.Tuple2;

public class Run {
  // During development, one can build a semi-engine, only add the first few layers. In this
  // particular example, we only add until Algorithm Layer.
  private static class HalfBakedEngineFactory implements IJavaEngineFactory {
    public JavaEngine<TrainingData, Integer, TrainingData, Double[], Double, Double> apply() {
      return new JavaEngineBuilder<TrainingData, Integer, TrainingData, Double[], Double, Double> ()
        .dataSourceClass(DataSource.class)
        .preparatorClass(Preparator.class)
        .addAlgorithmClass("OLS", OLSAlgorithm.class)
        .addAlgorithmClass("Default", DefaultAlgorithm.class)
        .build();
    }
  }

  public static void runComponents() throws IOException {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(new File("../data/lr_data.txt").getCanonicalPath()))
      .preparatorParams(new PreparatorParams(0.3))
      .addAlgorithmParams("OLS", new EmptyParams())
      .addAlgorithmParams("Default", new DefaultAlgorithmParams(0.2))
      .addAlgorithmParams("Default", new DefaultAlgorithmParams(0.4))
      .build();

    JavaWorkflow.runEngine(
        (new HalfBakedEngineFactory()).apply(),
        engineParams,
        new WorkflowParamsBuilder().batch("java regression engine").verbose(3).build()
        );
  }

  public static void runEngine() throws IOException {
    JavaEngineParams engineParams = new JavaEngineParamsBuilder()
      .dataSourceParams(new DataSourceParams(new File("../data/lr_data.txt").getCanonicalPath()))
      .preparatorParams(new PreparatorParams(0.3))
      .addAlgorithmParams("OLS", new EmptyParams())
      .addAlgorithmParams("Default", new DefaultAlgorithmParams(0.2))
      .addAlgorithmParams("Default", new DefaultAlgorithmParams(0.4))
      .build();

    JavaWorkflow.runEngine(
        (new EngineFactory()).apply(),
        engineParams,
        MeanSquareEvaluator.class,
        new EmptyParams(),
        new WorkflowParamsBuilder().batch("java regression engine").verbose(3).build()
        );
  }

  public static void main(String[] args) {
    try {
      runEngine();
      //runComponents();
    } catch (IOException ex) {
      System.out.println(ex);
    }
  }
}
