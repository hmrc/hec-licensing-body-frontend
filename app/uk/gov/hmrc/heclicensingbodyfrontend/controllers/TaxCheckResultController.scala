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

import cats.implicits.{catsKernelStdOrderForString, catsSyntaxEq}
import com.google.inject.Singleton
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.{RequestWithSessionData, SessionDataAction}
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckMatchResult
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckMatchResult.Match
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject

@Singleton
class TaxCheckResultController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  taxCheckValidPage: html.TaxCheckValid,
  mcc: MessagesControllerComponents
) extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  //If there is a tax code match, then there is no back link in there
  val taxCheckMatch: Action[AnyContent] = sessionDataAction { implicit request =>
    request.sessionData.taxCheckMatch match {
      case Some(taxCheckMatchResult) => getTaxResultPage(taxCheckMatchResult, "Match")
      case None                      =>
        logger.warn("Tax check match Result  not found")
        InternalServerError
    }
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

  //Reuse the same code in other pages
  //Will add cases for NoMatch and Expired in another ticket
  //for example case NoMatch(matchRequest, dateTime) if str === "NoMatch" => Ok(taxCheckNoMatchPage(matchRequest, dateTime))
  //Added str variable to make sure, if the case is not match , then it should go to case _
  private def getTaxResultPage(taxCheckMatchResult: HECTaxCheckMatchResult, str: String)(implicit
    request: RequestWithSessionData[_]
  ) = taxCheckMatchResult match {
    case Match(matchRequest, dateTime) if str === "Match" => Ok(taxCheckValidPage(matchRequest, dateTime))
    case _                                                =>
      logger.warn("Tax check match Result  not found")
      InternalServerError
  }

}
