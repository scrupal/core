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

import com.reactific.helpers.NotImplementedException

import java.io.InterruptedIOException
import java.sql.SQLTimeoutException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

import play.api._
import play.api.http.Status._
import play.api.http.{HttpErrorHandlerExceptions, HttpErrorHandler}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result, Results}
import scrupal.utils.ScrupalComponent

import scala.concurrent.Future
import scala.util.control.NonFatal

class ScrupalErrorHandler @Inject()(scrupal: Scrupal) extends HttpErrorHandler with ScrupalComponent {

  private def forSiteAndSubdomain(request: RequestHeader)
    (found: (RequestHeader, Site, Option[String]) ⇒ Future[Result])
    (orElse: () ⇒ Future[Result]): Future[Result] = {
    scrupal.siteForRequest(request) match {
      case (Some(site), Some(subDomain)) ⇒
        found(request, site, Some(subDomain))
      case (Some(site), None) ⇒
        found(request, site, None)
      case _ ⇒
        orElse()
    }
  }

  /**
    * Invoked when a client error occurs, that is, an error in the 4xx series.
    *
    * @param request The request that caused the client error.
    * @param statusCode The error status code.  Must be greater or equal to 400, and less than 500.
    * @param message The error message.
    */
  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    statusCode match {
      case BAD_REQUEST =>
        onBadRequest(request, message)
      case FORBIDDEN =>
        onForbidden(request, message)
      case NOT_FOUND =>
        onNotFound(request, message)
      case UNAUTHORIZED ⇒
        onUnauthorized(request, message)
      case clientError if statusCode >= 400 && statusCode < 500 =>
        onGenericClientError(request, statusCode, message)
      case nonClientError =>
        throw new IllegalArgumentException(s"onClientError invoked with non client error status code $statusCode: $message")
    }
  }

  def onBadRequest(request: RequestHeader, message: String): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onBadRequest(request, message, subDomain)
    } { () ⇒
      Future.successful(BadRequest(views.html.defaultpages.badRequest(request.method, request.uri, message)))
    }
  }

  def onForbidden(request: RequestHeader, message: String): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onForbidden(request, message, subDomain)
    } { () ⇒
      Future.successful(Forbidden(views.html.defaultpages.unauthorized()))
    }
  }

  def onNotFound(request: RequestHeader, message: String): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onNotFound(request, message, subDomain)
    } { () ⇒
      Future.successful(play.api.mvc.Results.NotFound(scrupal.environment.mode match {
        case Mode.Prod =>
          views.html.defaultpages.notFound(request.method, request.uri)
        case _ =>
          views.html.defaultpages.devNotFound(request.method, request.uri, Some(scrupal.router))
      }))
    }
  }

  def onUnauthorized(request: RequestHeader, message: String): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onUnauthorized(request, message, subDomain)
    } { () ⇒
      Future.successful(play.api.mvc.Results.Unauthorized(views.html.defaultpages.unauthorized()))
    }
  }

  protected def onGenericClientError(request: RequestHeader, status: Int, message: String): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onGenericClientError(request, status, message, subDomain)
    } { () ⇒
      Future.successful(
        Results.Status(status)(
          views.html.defaultpages.badRequest(request.method, request.uri, message)
        )
      )
    }
  }

  /**
    * Invoked when a server error occurs.
    *
    * By default, the implementation of this method delegates to [[onProdServerError]] when in prod mode, and
    * [[onDevServerError]] in dev mode.  It is recommended, if you want Play's debug info on the error page in dev
    * mode, that you override [[onProdServerError]] instead of this method.
    *
    * @param request The request that triggered the server error.
    * @param exception The server error.
    */
  def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    try {
      exception match {
        case x: NotImplementedError ⇒
          this.onNotImplemented(request, x)
        case x: NotImplementedException ⇒
          onNotImplemented(request, x)
        case x: TimeoutException ⇒
          onServiceUnavailable(request, x)
        case x: InterruptedException ⇒
          onServiceUnavailable(request, x)
        case x: SQLTimeoutException ⇒
          onServiceUnavailable(request, x)
        case x: InterruptedIOException ⇒
          onServiceUnavailable(request, x)
        case x : Throwable ⇒ {
          defaultServerError(request, x)
        }
      }
    } catch {
      case NonFatal(e) =>
        log.error("Error while handling error =>", e)
        Future.successful(InternalServerError)
    }
  }

  def defaultServerError(request : RequestHeader, exception: Throwable) : Future[Result] = {
    val usefulException = HttpErrorHandlerExceptions.throwableToUsefulException(scrupal.sourceMapper,
      scrupal.environment.mode == Mode.Prod, exception)
    logServerError(request, usefulException)
    scrupal.environment.mode match {
      case Mode.Prod =>
        onProdServerError(request, usefulException)
      case _ =>
        onDevServerError(request, usefulException)
    }
  }


  /**
    * Responsible for logging server errors.
    *
    * This can be overridden to add additional logging information, eg. the id of the authenticated user.
    *
    * @param request The request that triggered the server error.
    * @param usefulException The server error.
    */
  protected def logServerError(request: RequestHeader, usefulException: UsefulException) {
    log.error(s"! @${usefulException.id} - Internal server error, for (${request.method}) [${request.uri}] ->",
      usefulException
    )
  }

  private lazy val playEditor = scrupal.configuration.getString("play.editor")

  /**
    * Invoked in dev mode when a server error occurs.
    *
    * @param request The request that triggered the error.
    * @param exception The exception.
    */
  def onDevServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onDevServerError(request, exception, subDomain)
    } { () ⇒
      Future.successful(InternalServerError(views.html.defaultpages.devError(playEditor, exception)))
    }
  }

  /**
    * Invoked in prod mode when a server error occurs.
    *
    * Override this rather than [[onServerError]] if you don't want to change Play's debug output when logging errors
    * in dev mode.
    *
    * @param request The request that triggered the error.
    * @param exception The exception.
    */
  def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onProdServerError(request, exception, subDomain)
    } { () ⇒
      Future.successful(InternalServerError(views.html.defaultpages.error(exception)))
    }
  }

  def onNotImplemented(request: RequestHeader, exception: Throwable) : Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onNotImplemented(request, exception.getMessage, subDomain)
    } { () ⇒
      defaultServerError(request, exception)
    }
  }

  def onServiceUnavailable(request: RequestHeader, exception: Throwable) : Future[Result] = {
    forSiteAndSubdomain(request) { (header, site, subDomain) ⇒
      site.onServiceUnavailable(request, exception.getMessage, subDomain)
    } { () ⇒
      defaultServerError(request, exception)
    }
  }
}

