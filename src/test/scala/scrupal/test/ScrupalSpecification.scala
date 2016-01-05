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

package scrupal.test

import java.util.concurrent.atomic.AtomicInteger

import play.api.test.PlaySpecification
import scrupal.core._
import scrupal.utils.ScrupalComponent

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/** One line sentence description here.
  * Further description here.
  */
abstract class ScrupalSpecification(
  val specName : String,
  val additionalConfig : Map[String,AnyRef] = Map.empty[String,AnyRef],
  override val timeout : FiniteDuration = Duration(5, "seconds")
) extends PlaySpecification with ScrupalComponent {

  // WARNING: Do NOT put anything but def and lazy val because of DelayedInit or app startup will get invoked twice
  // and you'll have a real MESS on your hands!!!! (i.e. no db interaction will work!)

  implicit lazy val scrupal : Scrupal = ScrupalCache( ScrupalSpecification.next(specName),
    additionalConfiguration = additionalConfig )

  implicit lazy val site : Site = new FakeSite(SiteData(specName, "localhost"))

  implicit lazy val context : SiteContext = Context(scrupal, site)

  def withExecutionContext[T](f : ExecutionContext ⇒ T) : T = scrupal.withExecutionContext[T](f)

  /*
  def withStoreContext[T](f : StoreContext ⇒ T) : T =  scrupal.withStoreContext[T](f)

  def withSchema[T](schemaName : String)(f : Schema ⇒ T) : T =  scrupal.withSchema(schemaName)(f)

  def ensureSchema[T](d: SchemaDesign)(f : Schema ⇒ T) : Future[T] = {
    testScrupal.withExecutionContext { implicit ec : ExecutionContext ⇒
      scrupal.withStoreContext { sc : StoreContext ⇒
        sc.ensureSchema(d).map { schema: Schema ⇒ f(schema) }
      }
    }
  }
  */
}

object ScrupalSpecification {

  val counter = new AtomicInteger(0)

  def next(name: String): String = name + "-" + counter.incrementAndGet()

/*  def storageTestConfig(name: String): Option[Configuration] = {
    Some(
      ConfigHelpers.default() ++ Configuration(
        ConfigFactory.parseString(
          s"""storage {
             | scrupal {
             |   user :"",
             |   pass :"",
             |   uri  :"scrupal-mem://localhost/$name"
             | }
             |}""".stripMargin
        )
      )
    )
  }
  */
}


