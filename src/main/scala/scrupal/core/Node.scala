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

import java.time.Instant

import com.reactific.slickery.Storable.OIDType
import com.reactific.slickery.Slickery

import play.api.libs.iteratee.Enumerator

import scala.concurrent.{ExecutionContext, Future}
import scala.language.existentials

/** A function that generates content
  *
  * This is the basic characteristic of a Node. It is simply a function that receives a Context
  * and produces content as a Future Result. The Context provides the setting in which it is
  * generating the content. All dynamic content in Scrupal is generated through a Generator.
  * The Result embodies the notion of completing a request with some content and a disposition.
  */
trait ContentGenerator[CT <: Content[_]] extends ((Context) => Future[CT])

/** Content Generating Node
  *
  * This is the fundamental type of a node. It has some housekeeping information and can generate
  * some content in the form of an Array of Bytes. Modules define their own kinds of nodes by deriving
  * subclasses from this trait. As such, the Node class hierarchy defines the types of Nodes that
  * are possible to use with Scrupal. Note that Node instances are stored in the database and can be
  * very numerous. For that reason, they are not registered in an object registry.
  */
trait Node[CT <: Content[_]] extends ContentGenerator[CT]

object Node {
  object Empty extends StaticNode[EmptyContent] { val content = EmptyContent() }
}

/** A Node With Static Content
  * StaticNodes encapsulate a Content[_] which is then returned when asked for. This is the simplest kind of node
  * @tparam CT
  */
trait StaticNode[CT <: Content[_]] extends Node[CT] {
  def content : CT
  def apply(context : Context) : Future[CT] = {
    Future.successful { content }
  }
}

trait StoredNode[CT <: Content[_]] extends StaticNode[CT] with Slickery

case class StoredHtmlNode(
  name : String,
  description : String,
  content : HtmlContent,
  created : Instant = Instant.EPOCH,
  modified : Instant = Instant.EPOCH,
  oid : Option[OIDType] = None
) extends StoredNode[HtmlContent]

case class StoredJsonNode(
    name : String,
    description : String,
    content : JsonContent,
    created : Instant = Instant.EPOCH,
    modified : Instant = Instant.EPOCH,
    oid : Option[OIDType] = None
) extends StoredNode[JsonContent]

/*
abstract class CompoundNode extends Node {
  type TagsMap = Map[String, (Node, Response)]
  def subordinates : Map[String, Either[Reference[Node], Node]]
  def resolve(ctxt : Context, tagged_data : TagsMap) : Response
  def apply(ctxt : Context) : Future[Response] = {
    ctxt.withExecutionContext { implicit ec : ExecutionContext ⇒
      ctxt.withStoreContext { sc : StoreContext ⇒
        val futures_nested : Iterable[Future[(String, (Node, Response))]] = {
          for ((name, nr) ← subordinates) yield {
            if (nr.isLeft) {
              val nodeRef = nr.left.get
              val future = nodeRef.fetch(sc).map {
                case Some(node) ⇒ name → node(ctxt).map { r ⇒ node → r }
                case None ⇒ throw new Exception(s"$nodeRef not found.")
              }
              val f = future.flatMap {
                case (key, value) ⇒
                  value.map { er ⇒ key → er }
              }
              f
            } else {
              val node = nr.right.get
              val nested = node(ctxt).map { r ⇒ node → r }
              val f = nested.map { x ⇒ name → x }
              f
            }
          }
        }
        val futures_mapped = (Future sequence futures_nested).map { pairs ⇒ pairs.toMap }
        futures_mapped.map { x ⇒ resolve(ctxt, x) }
      }
    }
  }
}

case class LayoutProducer (
  template: Array[Byte],
  tags: Map[String,(Node,Response)]
) {
  private val end: Int = template.length
  private var index: Int = 0

  private val skip = 0
  private val found_one = 1
  private val found_two = 2
  private val found_three = 3
  private val consume_tag = 4
  private val found_tag = 5
  private val found_close_one = 6
  private val found_close_two = 7

  private def nextTag: (Option[String], Int, Int) = {
    var state: Int = 0
    var marker_start: Int = 0
    var tag_start: Int = 0
    var tag_len: Int = 0
    for (i ← index to end - 1) {
      if (template(i) == '@') {
        state match {
          case s:Int if s == skip ⇒ state = found_one; marker_start = i
          case s:Int if s == found_one ⇒ state = found_two
          case s:Int if s == found_two ⇒ state = found_three
          case s:Int if s == found_three ⇒ state = consume_tag;
          case s:Int if s == consume_tag ⇒ state = found_close_one; tag_len = i - tag_start
          case s:Int if s == found_close_one ⇒ state = found_close_two
          case s:Int if s == found_close_two ⇒
            return (Some(new String(template, tag_start, tag_len, utf8)), marker_start, i + 1)
          case _ ⇒ throw new Exception("Error in template state machine")
        }
      } else {
        state match {
          case s:Int if s == skip ⇒ /* just keep going */
          case s:Int if s == found_one ⇒ state = skip
          case s:Int if s == found_two ⇒ state = skip
          case s:Int if s == found_three ⇒ state = consume_tag; tag_start = i
          case s:Int if s == consume_tag ⇒ state = consume_tag
          case s:Int if s == found_close_one ⇒ state = skip
          case s:Int if s == found_close_two ⇒ state = skip
          case _ ⇒ throw new Exception("Error in template state machine")
        }
      }
    }
    // Didn't match any tags, don't update index
    (None, end, index)
  }

  def buildEnumerator(implicit ec: ExecutionContext) : Enumerator[Array[Byte]] = {
    var enums = Seq.empty[Enumerator[Array[Byte]]]
    while (index < end) {
      val (tag, block_end, next_index) = nextTag
      tag match {
        case Some(tagName) ⇒
          enums :+= Enumerator(template.slice(index, block_end))
          tags.get(tagName) match {
            case Some((node, en)) ⇒ enums :+= en.toEnumerator
            case none ⇒ enums :+= Enumerator(s"@@@ Missing Tag '$tagName' @@@".getBytes(utf8))
          }
          index = next_index
        case None ⇒
          enums :+= Enumerator(template.slice(index, end))
          index = end
      }
    }
    if (enums.isEmpty)
      Enumerator(Array.empty[Byte])
    else if (enums.length == 1) {
      enums.head
    } else {
      enums.tail.foldLeft(enums.head) { case (accu, next) ⇒ accu.andThen(next)}
    }
  }
}

/** A Node That Is A Layout.
  *
  * A Layout in Scrupal is an object that can generate a result, such as a page, with certain areas substituted by
  * node content. The substitutions are tracked by a mapping from tag name to a node reference. The node is generated
  * and substituted wherever the tag's name appears in the layout. The layout used by this node is also a reference
  * to a memory object. Scrupal defines a core set of layouts but other modules can define more.
  * @param description
  * @param subordinates
  * @param layout
  * @param modified
  * @param created
  */
case class LayoutNode (
  description: String,
  subordinates: Map[String, Either[Reference[Node], Node]],
  layout: Layout,
  modified: Option[Instant] = Some(Instant.now),
  created: Option[Instant] = Some(Instant.now),
  final val kind: Symbol = LayoutNode.kind
) extends CompoundNode {
  final val mediaType = layout.mediaType
  def resolve(ctxt: Context, tags: TagsMap) : EnumeratedResponse = {
    // val layout = Layout(layoutId).getOrElse(Layout.default)
    // FIXME: Generate the template -- val template: Array[Byte] = layout(tags, ctxt)
    val template = Array.empty[Byte]
    ctxt.withExecutionContext { implicit ec: ExecutionContext ⇒
      EnumeratedResponse(LayoutProducer(template, tags).buildEnumerator, mediaType)
    }
  }
}

object LayoutNode {
  final val kind = 'Layout

  /* TODO: Move LayoutNode DB stuff to ReactiveMongo project
  import BSONHandlers._
  object LayoutNodeVRW extends VariantReaderWriter[Node,LayoutNode] {
    implicit val LayoutNodeHandler : BSONHandler[BSONDocument,LayoutNode] = Macros.handler[LayoutNode]
    override def fromDoc(doc: BSONDocument): LayoutNode = LayoutNodeHandler.read(doc)
    override def toDoc(obj: Node): BSONDocument = LayoutNodeHandler.write(obj.asInstanceOf[LayoutNode])
  }
  Node.variants.register(kind, LayoutNodeVRW)
  */
}
*/
