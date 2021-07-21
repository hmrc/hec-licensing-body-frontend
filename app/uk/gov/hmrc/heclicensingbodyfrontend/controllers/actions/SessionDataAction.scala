/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.Results.{InternalServerError, Redirect}
import play.api.mvc.{ActionBuilder, ActionFunction, AnyContent, BodyParser, MessagesControllerComponents, Request, Result, WrappedRequest}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.routes
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECSession
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging.LoggerOps
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.toFuture
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

final case class RequestWithSessionData[A](
  request: Request[A],
  sessionData: HECSession
) extends WrappedRequest[A](request)

@Singleton
class SessionDataAction @Inject() (
  sessionStore: SessionStore,
  mcc: MessagesControllerComponents
)(implicit val executionContext: ExecutionContext)
    extends ActionFunction[Request, RequestWithSessionData]
    with ActionBuilder[RequestWithSessionData, AnyContent]
    with Logging {

  override def invokeBlock[A](
    request: Request[A],
    block: RequestWithSessionData[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    sessionStore
      .get()
      .foldF[Result](
        { e =>
          logger.warn("Could not get session data", e)
          InternalServerError
        },
        {
          case None          => Redirect(routes.StartController.start())
          case Some(session) => block(RequestWithSessionData(request, session))
        }
      )
  }

  override def parser: BodyParser[AnyContent] = mcc.parsers.default

}
