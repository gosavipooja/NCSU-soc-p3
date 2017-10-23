name := """project3"""
organization := "com.p3"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.2"

libraryDependencies += guice

resolvers += "public-jboss" at "http://repository.jboss.org/nexus/content/groups/public-jboss/"
libraryDependencies ++= Seq( "org.drools" % "drools-core" % "7.3.0.Final", "org.drools" % "drools-compiler" % "7.3.0.Final")
libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value