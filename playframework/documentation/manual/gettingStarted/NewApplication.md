<!--- Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com> -->
# Creating a new application

## Create a new application with the activator command

The `activator` command can be used to create a new Play application.  Activator allows you to select a template that your new application should be based off.  For vanilla Play projects, the names of these templates are `play-scala` for Scala based Play applications, and `play-java` for Java based Play applications.

> Note that choosing a template for either Scala or Java at this point does not imply that you can’t change language later. For example, you can create a new application using the default Java application template and start adding Scala code whenever you like.

To create a new vanilla Play Scala application, run:

```bash
$ activator new my-first-app play-scala
```

To create a new vanilla Play Java application, run:

```bash
$ activator new my-first-app play-java
```

In either case, you can replace `my-first-app` with whatever name you want your application to use.  Activator will use this as the directory name to create the application in.  You can change this name later if you choose.

[[images/activatorNew.png]]

> If you wish to use other Activator templates, you can do this by running `activator new`. This will prompt you for an application name, and then give you a chance to browse and select an appropriate template.

Once the application has been created you can use the `activator` command again to enter the [[Play console|PlayConsole]].

```bash
$ cd my-first-app
$ activator
```

## Create a new application with the Activator UI

New Play applications can also be created with the Activator UI.  To use the Activator UI, run:

```bash
$ activator ui
```

You can read the documentation for using the Activator UI [here](https://lightbend.com/activator/docs).

## Create a new application without Activator

It is also possible to create a new Play application without installing Activator, using sbt directly.

> First install [sbt](http://www.scala-sbt.org/) if needed.

Create a new directory for your new application and configure your sbt build script with two additions.

In `project/plugins.sbt`, add:

```scala
// The Typesafe repository
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/maven-releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "%PLAY_VERSION%")
```

Be sure to replace `%PLAY_VERSION%` here by the exact version you want to use. If you want to use a snapshot version, you will have to specify this additional resolver:

```scala
// Typesafe snapshots
resolvers += "Typesafe Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
```

To ensure the proper sbt version is used, make sure you have the following in `project/build.properties`:

```
sbt.version=0.13.11
```

In `build.sbt` for Java projects:

```scala
name := "my-first-app"

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayJava)
```

...or Scala projects:

```scala
name := "my-first-app"

version := "1.0.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
```

You can then launch the sbt console in this directory:

```bash
$ cd my-first-app
$ sbt
```

sbt will load your project and fetch the dependencies.

By default, the `PlayJava` and `PlayScala` plugins do not depend on any specific dependency injection solution. If you want to use Play's Guice module, add `guiceSupport` to your library dependencies:

```scala
libraryDependencies += guiceSupport
```

Be aware that you should either be manually defining an `ApplicationLoader` or have a dependency on another module (such as `guiceSupport`) that provides one.
