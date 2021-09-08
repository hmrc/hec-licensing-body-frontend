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

import com.google.inject.Singleton
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject

@Singleton
class TaxCheckResultController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  mcc: MessagesControllerComponents
) extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  //If there is a tax code match, then there is no back link in there
  val taxCheckMatch: Action[AnyContent] = sessionDataAction { implicit request =>
    Ok(
      s"Session is ${request.sessionData}}"
    )
  }

  //If tax check code comes as Expired, then user click on View another tax check code button and
  // it takes back to start of the LB journey, so no back link
  val taxCheckExpired: Action[AnyContent] = sessionDataAction { implicit request =>
    Ok(
      s"Session is ${request.sessionData}}"
    )
  }

//back link is there only in case of NO match
  val taxCheckNotMatch: Action[AnyContent] = sessionDataAction { implicit request =>
    Ok(
      s"Session is ${request.sessionData} back Url ::${journeyService.previous(routes.TaxCheckResultController.taxCheckNotMatch())}"
    )
  }

}
