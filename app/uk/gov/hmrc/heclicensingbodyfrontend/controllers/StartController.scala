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

package uk.gov.hmrc.heclicensingbodyfrontend.controllers

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECSession
import uk.gov.hmrc.heclicensingbodyfrontend.models.UserAnswers.IncompleteUserAnswers
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

@Singleton
class StartController @Inject() (
  sessionStore: SessionStore,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with Logging {

  val start: Action[AnyContent] = Action.async { implicit request =>
    val newSession = HECSession(IncompleteUserAnswers.empty)
    sessionStore
      .store(newSession)
      .fold(
        { e =>
          logger.warn("Could not store session", e)
          InternalServerError
        },
        _ => Redirect(routes.HECTaxCheckCodeController.hecTaxCheckCode())
      )
  }

}
