/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.heclicensingbodyfrontend.models.{HECSession, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext

@Singleton
class StartController @Inject() (
  sessionStore: SessionStore,
  journeyService: JourneyService,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with Logging {

  val start: Action[AnyContent] = Action.async { implicit request =>
    val newSessionStore = for {
      hecSession <- sessionStore.get()
      newSession  = hecSession match {
                      case Some(session) => session.copy(UserAnswers.empty, None, session.verificationAttempts)
                      case None          => HECSession(UserAnswers.empty, None, Map.empty)
                    }
      _          <- sessionStore.store(newSession)
    } yield ()
    newSessionStore
      .fold(
        _.doThrow("Could not store session"),
        _ => Redirect(journeyService.firstPage)
      )
  }

}
