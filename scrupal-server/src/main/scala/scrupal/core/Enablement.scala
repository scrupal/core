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

package scrupal.core

import com.reactific.helpers.{AbstractRegistry, Identifiable, IdentifiedWithRegistry}
import scrupal.utils.ScrupalComponent

import scala.collection.mutable
import scala.language.reflectiveCalls

/** Enablement Trait For Tracking Enable/Disable Status of Enablees.
  *
  * This is intended to be mixed in to some container of other Enablement objects in a hierarchy that eventually
  * contains Enablees, or has them contained at any level. Enablement objects can also be Enablees. For example,
  * a Module contains Features and Entities that are both Enablees so it is an Enablement object and it tracks the
  * enablement status of its enablees.  But the Module can be enabled/disabled itself so it can also be an Enablee
  */

trait Enablement[T <: Enablement[T]] extends IdentifiedWithRegistry with ScrupalComponent {

  private val _enabled = new AbstractRegistry[Enablee, mutable.HashSet[Enablement[_]]] {
    def register(key : Enablee, obj : mutable.HashSet[Enablement[_]]) = _register(key, obj)
    def unregister(key : Enablee) = _unregister(key)
  }

  def isChildScope(scope : Enablement[_]) : Boolean

  /** Determine if an Enablee is enabled within a scope.
    *
    * The enablee is only enabled if the following cases are true:
    *
    * - It and all its parent Enablees are enabled in forScope
    * -
    *
    * @param enablee The thing about which we are determining the enablement status
    * @param forScope The scope in which enablement matters
    * @return True iff the enablee is enabled in forScope
    */
  final def isEnabled(enablee : Enablee, forScope : Enablement[_] = this) : Boolean = {
    if (forScope != this && !isChildScope(forScope))
      toss(s"Scope ${forScope.id.name} is not a child of ${id.name} " +
        s"so enablement for ${enablee.id.name} cannot be determined.")
    _enabled.lookup(enablee) match {
      case None ⇒ false
      case Some(set) ⇒
        set.contains(forScope) && (
          enablee.parent match {
            case None ⇒ true
            case Some(e : Enablee) ⇒
              e.isEnabled(forScope)
          }
        )
    }
  }

  def enable(enablee : Enablee, forScope : Enablement[_] = this) : Unit = {
    if (forScope != this && !isChildScope(forScope))
      toss(s"Scope ${forScope.id} is not a child of $id so ${enablee.enablementName} cannot be enabled for it.")
    val update_value : mutable.HashSet[Enablement[_]] = _enabled.lookup(enablee) match {
      case Some(set) ⇒ set + forScope
      case None ⇒ mutable.HashSet(forScope)
    }
    _enabled.register(enablee, update_value)
  }

  def enable(enablee: Option[Enablee]) : Unit = {
    enablee.foreach { e ⇒ enable(e) }
  }

  def enable(enablee: Option[Enablee], forScope : Option[Enablement[_]]) : Unit = {
    enablee.foreach { e ⇒ forScope.foreach { fs ⇒ enable(e, fs) }}
  }

  def disable(enablee : Enablee, forScope : Enablement[_] = this) : Unit = {
    if (forScope != this && !isChildScope(forScope))
      toss(s"Scope ${forScope.id} is not a child of $id so ${enablee.enablementName} cannot be disabled for it.")
    _enabled.lookup(enablee) match {
      case Some(set) ⇒
        val update_value : mutable.HashSet[Enablement[_]] = set - forScope
        if (update_value.isEmpty)
          _enabled.unregister(enablee)
        else
          _enabled.register(enablee, update_value)
      case None ⇒
        log.debug(s"Attempt to disable ${enablee.enablementName} that isn't enabled.")
    }
  }

  def mapIf[R](condition : Enablee ⇒ Boolean)(action : Enablee ⇒ R) : Iterable[R] = {
    for (e ← _enabled.keys if condition(e)) yield { action(e) }
  }

  def mapEnabled[R](f : Enablee ⇒ R) : Iterable[R] = {
    mapIf(_.isEnabled(this))(f)
  }

}

/** Something that can be enabled or disabled.
  *
  * Enablee objects have parents and the Enablee is only enabled in some Enablement scope if its parent is too. So
  * if you disable a module in a particular scope, all of its features and entities become disabled in that scope too
  * even without adjusting their enablement status. If the module is re-enabled, it comes back with the previous
  * enablement status at the lower level (entities and features).
  *
  * Note that enablement is NOT recorded in the Enablee directly even though the convenience methods might seem to
  * indicate that. All the enabled/disabled status is recorded in the Enablement objects.
  */
trait Enablee extends Identifiable {
  def enablementName = id.name
  def parent : Option[Enablee] = None
  def isEnabled(scope : Enablement[_]) : Boolean = { scope.isEnabled(this) }
  def isEnabled(scope : Enablement[_], how : Boolean) : Boolean = { scope.isEnabled(this) == how }
  def enable(scope : Enablement[_]) : this.type = { scope.enable(this); this }
  def enable(scope : Enablement[_], how : Boolean) : this.type = {
    how match {
      case true  ⇒ scope.enable(this)
      case false ⇒ scope.disable(this)
    }
    this
  }
  def disable(scope : Enablement[_]) : this.type = { scope.disable(this); this }
}

