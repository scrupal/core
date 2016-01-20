/**********************************************************************************************************************
 * This file is part of Scrupal, a Scalable Reactive Web Application Framework for Content Management                 *
 *                                                                                                                    *
 * Copyright (c) 2015, Reactific Software LLC. All Rights Reserved.                                                   *
 *                                                                                                                    *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance     *
 * with the License. You may obtain a copy of the License at                                                          *
 *                                                                                                                    *
 *     http://www.apache.org/licenses/LICENSE-2.0                                                                     *
 *                                                                                                                    *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed   *
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for  *
 * the specific language governing permissions and limitations under the License.                                     *
 **********************************************************************************************************************/

import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.{PlayScala, PlayLayoutPlugin}
import play.sbt.routes.RoutesKeys._
import play.twirl.sbt.Import.TwirlKeys
import play.twirl.sbt.SbtTwirl
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo.BuildInfoPlugin
import scrupal.sbt.ScrupalPlugin
import scrupal.sbt.ScrupalPlugin.autoImport._
import scoverage.ScoverageSbtPlugin
import scoverage.ScoverageSbtPlugin.ScoverageKeys._
import org.scoverage.coveralls.Imports.CoverallsKeys._

object ScrupalCoreBuild extends Build with AssetsSettings with Dependencies {

  val classesIgnoredByScoverage : String = Seq[String](
    "<empty>", // Avoids warnings from scoverage
    "scrupal.core.ScrupalBuildInfo", // Generated by sbt-buildinfo
    "scrupal.test.*", // Code used only for testing
    "scrupal.core.akkahttp.*", // Not currently in use, may be deleted in future
    "router.Routes.*", // Stuff generated by the Play Routes compiler
    "router.scrupal.core.javascript.*" // Javascript routes generated by Play Routes compiler

  ).mkString(";")

  lazy val root = Project("scrupal-core", file("."))
    .disablePlugins(PlayLayoutPlugin)
    .enablePlugins(PlayScala, BuildInfoPlugin, ScrupalPlugin, ScoverageSbtPlugin, SbtTwirl)
    // .settings(sbt_web_settings)
    .settings(pipeline_settings)
    .settings(less_settings)
    .settings(
      organization := "org.scrupal",
      copyrightHolder := "Reactific Software LLC",
      copyrightYears := Seq(2013, 2014, 2015),
      developerUrl := url("http://reactific.com/"),
      titleForDocs := "Scrupal Core",
      codePackage := "scrupal.core",
      resolvers ++= all_resolvers,
      libraryDependencies ++= core_dependencies,
      routesGenerator := InjectedRoutesGenerator,
      ivyLoggingLevel := UpdateLogging.Quiet,
      TwirlKeys.templateImports += "scrupal.core._",
      namespaceReverseRouter := true,
      coverageFailOnMinimum := true,
      coverageExcludedPackages := classesIgnoredByScoverage,
      coverageMinimum := 75,
      coverallsToken := Some("uoZrsbhbC0E2289tvwp3ISntZLH2yjwqX"),
      buildInfoObject := "ScrupalBuildInfo",
      buildInfoPackage := "scrupal.core",
      buildInfoKeys ++= Seq (
        "play_version" -> Ver.play,
        "akka_version" -> Ver.akka,
        "silhouette_version" -> Ver.silhouette,
        "bootstrap_version" -> Ver.bootstrap,
        "bootswatch_version" -> Ver.bootswatch,
        "font_awesome_version" -> Ver.font_awesome,
        "marked_version" -> Ver.marked,
        "jquery_version" → Ver.jquery,
        "modernizr_version" → Ver.modernizr
      ),
      unmanagedJars in sbt.Test <<= baseDirectory map { base => (base / "libs" ** "*.jar").classpath },
      maxErrors := 50
    )

  override def rootProject = Some(root)
}
