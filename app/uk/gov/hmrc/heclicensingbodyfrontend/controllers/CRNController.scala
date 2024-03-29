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

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.{RequestWithSessionData, SessionDataAction}
import uk.gov.hmrc.heclicensingbodyfrontend.models.AuditEvent.TaxCheckCodeChecked
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.Company
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult, InconsistentSessionState}
import uk.gov.hmrc.heclicensingbodyfrontend.services.{AuditService, HECTaxMatchService, JourneyService, VerificationService}
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CRNController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  taxMatchService: HECTaxMatchService,
  verificationService: VerificationService,
  auditService: AuditService,
  crnPage: html.CompanyRegistrationNumber,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  import CRNController._

  val companyRegistrationNumber: Action[AnyContent] = sessionDataAction { implicit request =>
    val back = journeyService.previous(routes.CRNController.companyRegistrationNumber)
    val crn  = request.sessionData.userAnswers.crn
    val form = crn.fold(crnForm)(crnForm.fill)
    Ok(crnPage(form, back))
  }

  val companyRegistrationNumberSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    val taxCheckCode =
      request.sessionData.userAnswers.taxCheckCode
        .getOrElse(InconsistentSessionState("Could not find tax check code").doThrow)
    val licenceType  =
      request.sessionData.userAnswers.licenceType
        .getOrElse(InconsistentSessionState("Could not find licence type").doThrow)

    def updateAndGoToNextPage(crn: CRN): Future[Result] =
      journeyService
        .updateAndNext(
          routes.CRNController.companyRegistrationNumber,
          request.sessionData.copy(userAnswers = request.sessionData.userAnswers.copy(crn = Some(crn)))
        )
        .fold(
          _.doThrow("Could not update session and proceed"),
          Redirect
        )

    crnForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Ok(
            crnPage(
              formWithErrors,
              journeyService.previous(routes.CRNController.companyRegistrationNumber)
            )
          ),
        crn =>
          if (verificationService.maxVerificationAttemptReached(taxCheckCode)(request.sessionData)) {
            auditTaxCheckResult(crn, taxCheckCode, licenceType, None, request.sessionData)
            updateAndGoToNextPage(crn)
          } else handleValidCrn(crn, taxCheckCode, licenceType)
      )

  }

  private def handleValidCrn(crn: CRN, taxCheckCode: HECTaxCheckCode, licenceType: LicenceType)(implicit
    request: RequestWithSessionData[_]
  ): Future[Result] =
    getTaxMatchResult(crn, taxCheckCode, licenceType)
      .fold(
        _.doThrow("Couldn't match tax check"),
        Redirect
      )

  private def getTaxMatchResult(crn: CRN, taxCheckCode: HECTaxCheckCode, licenceType: LicenceType)(implicit
    request: RequestWithSessionData[_]
  ): EitherT[Future, Error, Call] =
    for {
      taxMatch      <- taxMatchService.matchTaxCheck(HECTaxCheckMatchRequest(taxCheckCode, licenceType, Left(crn)))
      updatedSession =
        verificationService.updateVerificationAttemptCount(taxMatch, taxCheckCode, Left(crn))(request.sessionData)
      _              = auditTaxCheckResult(crn, taxCheckCode, licenceType, Some(taxMatch), updatedSession)
      next          <- journeyService
                         .updateAndNext(routes.CRNController.companyRegistrationNumber, updatedSession)
    } yield next

  private def auditTaxCheckResult(
    crn: CRN,
    taxCheckCode: HECTaxCheckCode,
    licenceType: LicenceType,
    matchResult: Option[HECTaxCheckMatchResult],
    session: HECSession
  )(implicit hc: HeaderCarrier, r: RequestWithSessionData[_]): Unit = {
    val submittedData =
      TaxCheckCodeChecked.SubmittedData(
        taxCheckCode,
        Company,
        licenceType,
        None,
        Some(crn)
      )

    val auditEvent =
      matchResult.fold(
        TaxCheckCodeChecked.blocked(submittedData, r.language)
      ) { m =>
        TaxCheckCodeChecked(
          m.status,
          submittedData,
          session.verificationAttempts.get(taxCheckCode).exists(_.lockExpiresAt.nonEmpty),
          r.language,
          m.status.matchFailureReason
        )
      }

    auditService.sendEvent(auditEvent)
  }

}

object CRNController {

  // This regex checks first two characters as alphanumeric but the next 5/6 chars should be number
  private val crnRegex = "^[A-Z0-9]{2}[0-9]{5,6}"

  // Checking CRN constraint based on rules on following priority
  // Should have only alphanumeric characters
  // Should be in correct format - first two chars alphanumeric and rest 5/6 chars as number
  // and Should have only either 7 or 8 characters
  private val crnConstraint: Constraint[CRN] =
    Constraint(code =>
      if (!code.value.forall(_.isLetterOrDigit)) Invalid("error.nonAlphanumericChars")
      else if (code.value.matches(crnRegex)) Valid
      else Invalid("error.crnInvalid")
    )

  // CRN Form instance
  val crnForm: Form[CRN] =
    Form(
      mapping(
        "crn" -> nonEmptyText
          .transform[CRN](
            s => CRN(s.removeWhitespace.toUpperCase(Locale.UK)),
            _.value
          )
          .verifying(crnConstraint)
      )(identity)(Some(_))
    )
}
