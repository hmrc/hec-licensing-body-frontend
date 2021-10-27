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
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckMatchResult
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
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
  taxCheckExpiredPage: html.TaxCheckExpired,
  taxCheckNoMatchPage: html.TaxCheckNoMatch,
  tooManyAttemptsPage: html.TooManyAttempts,
  mcc: MessagesControllerComponents
) extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  //If there is a tax code match, then there is no back link in there
  val taxCheckMatch: Action[AnyContent] = sessionDataAction { implicit request =>
    request.sessionData.taxCheckMatch match {
      case Some(HECTaxCheckMatchResult(taxCheckMatchResult, dateTime, Match)) =>
        Ok(taxCheckValidPage(taxCheckMatchResult, dateTime))
      case _                                                                  =>
        logger.warn("Tax check match Result not found for 'Match' page")
        InternalServerError
    }
  }

  //If tax check code comes as Expired, then user click on View another tax check code button and
  // it takes back to start of the LB journey, so no back link
  val taxCheckExpired: Action[AnyContent] = sessionDataAction { implicit request =>
    request.sessionData.taxCheckMatch match {
      case Some(HECTaxCheckMatchResult(taxCheckMatchResult, dateTime, Expired)) =>
        Ok(taxCheckExpiredPage(taxCheckMatchResult, dateTime))
      case _                                                                    =>
        logger.warn("Tax check match Result not found for 'Expired' page")
        InternalServerError

    }
  }

//back link is there only in case of NO match
  val taxCheckNotMatch: Action[AnyContent] = sessionDataAction { implicit request =>
    val back: Call = journeyService.previous(routes.TaxCheckResultController.taxCheckNotMatch())
    request.sessionData.taxCheckMatch match {
      case Some(HECTaxCheckMatchResult(taxCheckMatchResult, _, NoMatch)) =>
        Ok(
          taxCheckNoMatchPage(
            taxCheckMatchResult,
            back
          )
        )
      case _                                                             =>
        logger.warn("Tax check match Result not found for 'No Match' page")
        InternalServerError
    }
  }

  val tooManyVerificationAttempts: Action[AnyContent] = sessionDataAction { implicit request =>
    val userAnswers = request.sessionData.userAnswers
    userAnswers.taxCheckCode match {
      case Some(taxCheckCode) =>
        val lockAttemptExpiresAt = request.sessionData.verificationAttempts
          .get(taxCheckCode)
          .flatMap(_.lockAttemptReleasedAt)
        Ok(tooManyAttemptsPage(userAnswers, lockAttemptExpiresAt))
      case None               =>
        logger.warn("Tax check code  is not found in session ")
        InternalServerError
    }
  }

}
