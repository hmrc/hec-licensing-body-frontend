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

import cats.instances.future.*
import com.google.inject.Inject
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckCode
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}

class HECTaxCheckCodeController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  hecTaxCheckCodePage: html.HECTaxCheckCode,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  import HECTaxCheckCodeController.*

  val hecTaxCheckCode: Action[AnyContent] = sessionDataAction { implicit request =>
    val taxCheckCode = request.sessionData.userAnswers.taxCheckCode
    val form         = taxCheckCode.fold(taxCheckCodeForm)(taxCheckCodeForm.fill)
    val back         = journeyService.previous(routes.HECTaxCheckCodeController.hecTaxCheckCode)
    Ok(hecTaxCheckCodePage(form, back))
  }

  val hecTaxCheckCodeSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    def handleValidTaxCheckCode(taxCheckCode: HECTaxCheckCode): Future[Result] = {
      val updatedAnswers = request.sessionData.userAnswers.copy(taxCheckCode = Some(taxCheckCode))
      val updatedSession = request.sessionData.copy(userAnswers = updatedAnswers)

      journeyService
        .updateAndNext(routes.HECTaxCheckCodeController.hecTaxCheckCode, updatedSession)
        .fold(
          _.doThrow("Could not store session and find next location"),
          Redirect
        )
    }

    taxCheckCodeForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Ok(
            hecTaxCheckCodePage(
              formWithErrors,
              journeyService.previous(routes.HECTaxCheckCodeController.hecTaxCheckCode)
            )
          ),
        handleValidTaxCheckCode
      )
  }

}

object HECTaxCheckCodeController {

  private val invalidAlphanumericChars: List[Char] =
    List('I', 'O', 'S', 'U', 'V', 'W', '0', '1', '5')

  private val taxCheckCodeConstraint: Constraint[HECTaxCheckCode] =
    Constraint(code =>
      if (code.value.length > 9) Invalid("error.tooLong")
      else if (code.value.length < 9) Invalid("error.tooShort")
      else if (!code.value.forall(_.isLetterOrDigit)) Invalid("error.nonAlphanumericChars")
      else if (code.value.intersect(invalidAlphanumericChars).nonEmpty) Invalid("error.invalidAlphanumericChars")
      else Valid
    )

  val taxCheckCodeForm: Form[HECTaxCheckCode] =
    Form(
      mapping(
        "taxCheckCode" -> nonEmptyText
          .transform[HECTaxCheckCode](
            s => HECTaxCheckCode(s.removeWhitespace.toUpperCase(Locale.UK)),
            _.value
          )
          .verifying(taxCheckCodeConstraint)
      )(identity)(Some(_))
    )

}
