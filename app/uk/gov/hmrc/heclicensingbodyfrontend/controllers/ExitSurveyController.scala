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

import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.hmrcfrontend.config
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

@Singleton
class ExitSurveyController @Inject() (
  mcc: MessagesControllerComponents,
  appConfig: AppConfig
) extends FrontendController(mcc) {

  val exitSurvey: Action[AnyContent] = Action { _ =>
    val signOutUrl = appConfig.signOutWithFeedbackUrl
    Redirect(signOutUrl)
  }

}
