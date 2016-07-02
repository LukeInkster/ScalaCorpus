import AssemblyKeys._

assemblySettings

name := "template-scala-parallel-recommendation-custom-query"

organization := "io.prediction"

def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

libraryDependencies ++= provided(
  "io.prediction"    %% "core"          % "0.8.6",
  "org.apache.spark" %% "spark-core"    % "1.2.0",
  "org.apache.spark" %% "spark-mllib"   % "1.2.0")
