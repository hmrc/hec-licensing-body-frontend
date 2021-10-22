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

import cats.data.EitherT
import cats.implicits.catsSyntaxEq
import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, of}
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.DateOfBirthController.dateOfBirthForm
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.{RequestWithSessionData, SessionDataAction}
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus.NoMatch
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, Error, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult}
import uk.gov.hmrc.heclicensingbodyfrontend.services.{HECTaxMatchService, JourneyService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging.LoggerOps
import uk.gov.hmrc.heclicensingbodyfrontend.util.{Logging, TimeUtils}
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DateOfBirthController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  taxMatchService: HECTaxMatchService,
  dateOfBirthPage: html.DateOfBirth,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  val dateOfBirth: Action[AnyContent] = sessionDataAction { implicit request =>
    val back        = journeyService.previous(routes.DateOfBirthController.dateOfBirth())
    val dateOfBirth = request.sessionData.userAnswers.dateOfBirth
    val form = {
      val emptyForm = dateOfBirthForm()
      dateOfBirth.fold(emptyForm)(emptyForm.fill)
    }
    Ok(dateOfBirthPage(form, back))
  }

  val dateOfBirthSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    def goToNextPage: Future[Result] =
      journeyService
        .updateAndNext(
          routes.DateOfBirthController.dateOfBirth(),
          request.sessionData
        )
        .fold(
          { e =>
            logger.warn("Could not update session and proceed", e)
            InternalServerError
          },
          Redirect
        )

    def getTaxCheckAttempts(hecTaxCheckCode: HECTaxCheckCode) =
      request.sessionData.attempts.get(hecTaxCheckCode.value).getOrElse(0)

    def maxAttemptReached(hecTaxCheckCode: HECTaxCheckCode) =
      getTaxCheckAttempts(hecTaxCheckCode: HECTaxCheckCode) >= appConfig.maxVerificationAttempts

    val taxCheckCodeOpt = request.sessionData.userAnswers.taxCheckCode
    val attempts        = request.sessionData.attempts

    def action: Future[Result] = dateOfBirthForm()
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Ok(
            dateOfBirthPage(formWithErrors, journeyService.previous(routes.DateOfBirthController.dateOfBirth()))
          ),
        handleValidDateOfBirth
      )

    (taxCheckCodeOpt, attempts) match {
      case (Some(taxCheckCode), m)
          if (m.isEmpty) || !m.keySet.contains(taxCheckCode.value) || !(maxAttemptReached(taxCheckCode)) =>
        action
      case (Some(taxCheckCode), m) if m.keySet.contains(taxCheckCode.value) && (maxAttemptReached(taxCheckCode)) =>
        goToNextPage
      case _                                                                                                     => InternalServerError
    }
  }

  private def handleValidDateOfBirth(dob: DateOfBirth)(implicit
    r: RequestWithSessionData[_]
  ): Future[Result] =
    getTaxMatchResult(dob)
      .fold(
        { e =>
          logger.warn(" Couldn't get tax check code", e)
          InternalServerError
        },
        Redirect
      )

  private def getTaxMatchResult(
    dateOfBirth: DateOfBirth
  )(implicit request: RequestWithSessionData[_]): EitherT[Future, Error, Call] = {

    val hecTaxCheckCode = request.sessionData.userAnswers.taxCheckCode
    val licenceType     = request.sessionData.userAnswers.licenceType
    (hecTaxCheckCode, licenceType) match {
      case (Some(taxCheckCode), Some(lType)) =>
        for {
          taxMatch      <- taxMatchService.matchTaxCheck(HECTaxCheckMatchRequest(taxCheckCode, lType, Right(dateOfBirth)))
          updatedSession = getUpdatedSession(taxMatch, taxCheckCode, dateOfBirth)
          next          <- journeyService
                             .updateAndNext(routes.DateOfBirthController.dateOfBirth(), updatedSession)
        } yield next
      case _                                 =>
        EitherT.leftT(
          Error(
            Left(
              "Insufficient data to proceed and submit, one or both of : HECTaxCheckCode or LicenceType are missing"
            )
          )
        )
    }
  }

  private def getUpdatedSession(
    taxMatch: HECTaxCheckMatchResult,
    taxCheckCode: HECTaxCheckCode,
    dateOfBirth: DateOfBirth
  )(implicit request: RequestWithSessionData[_]) = {
    val updatedAnswers         = request.sessionData.userAnswers.copy(dateOfBirth = Some(dateOfBirth))
    val currentAttemptMap      = request.sessionData.attempts
    val currentTaxCheckAttempt = currentAttemptMap.get(taxCheckCode.value).getOrElse(0)
    if (taxMatch.status === NoMatch) {
      request.sessionData.copy(
        userAnswers = updatedAnswers,
        Some(taxMatch),
        attempts = currentAttemptMap ++ Map(taxCheckCode.value -> (currentTaxCheckAttempt + 1))
      )
    } else {
      request.sessionData.copy(
        userAnswers = updatedAnswers,
        Some(taxMatch),
        attempts = currentAttemptMap ++ Map(taxCheckCode.value -> 0)
      )
    }

  }
}

object DateOfBirthController {

  def dateOfBirthForm()(implicit message: Messages): Form[DateOfBirth] = {
    val key                           = "dateOfBirth"
    val futureDate                    = TimeUtils.today().plusDays(1L)
    val tooFarInPastDate              = LocalDate.of(1900, 1, 1)
    val futureDateArgs: Seq[String]   = Seq(TimeUtils.govDisplayFormat(futureDate))
    val tooFarInPastArgs: Seq[String] = Seq(TimeUtils.govDisplayFormat(tooFarInPastDate))
    Form(
      mapping(
        "" -> of(
          TimeUtils.dateFormatter(
            Some(futureDate),
            Some(tooFarInPastDate),
            s"$key-day",
            s"$key-month",
            s"$key-year",
            key,
            tooFarInFutureArgs = futureDateArgs,
            tooFarInPastArgs = tooFarInPastArgs
          )
        )
      )(DateOfBirth(_))(d => Some(d.value))
    )
  }
}
