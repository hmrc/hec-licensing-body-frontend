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
import com.google.inject.Inject
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.data.Forms.{mapping, nonEmptyText}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.models.{HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging.LoggerOps
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.heclicensingbodyfrontend.views.html

import java.util.Locale
import scala.concurrent.ExecutionContext

class HECTaxCheckCodeController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  hecTaxCheckCodePage: html.HECTaxCheckCode,
  mcc: MessagesControllerComponents
)(implicit appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  import HECTaxCheckCodeController._

  val hecTaxCheckCode: Action[AnyContent] = sessionDataAction { implicit request =>
    val taxCheckCode = request.sessionData.userAnswers.taxCheckCode
    val form         = taxCheckCode.fold(taxCheckCodeForm)(taxCheckCodeForm.fill)
    Ok(hecTaxCheckCodePage(form))
  }

  val hecTaxCheckCodeSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    taxCheckCodeForm
      .bindFromRequest()
      .fold(
        formWithErrors => Ok(hecTaxCheckCodePage(formWithErrors)),
        taxCheckCode =>
          journeyService
            .updateAndNext(
              routes.HECTaxCheckCodeController.hecTaxCheckCode(),
              (HECSession.userAnswers composeLens UserAnswers.taxCheckCode)
                .set(Some(taxCheckCode))(request.sessionData)
            )
            .fold(
              { e =>
                logger.warn("Could not store session and find next location", e)
                InternalServerError
              },
              Redirect
            )
      )
  }

}

object HECTaxCheckCodeController {

  val taxCheckCodeForm: Form[HECTaxCheckCode] =
    Form(
      mapping(
        "taxCheckCode" -> nonEmptyText
          .transform[HECTaxCheckCode](
            s => HECTaxCheckCode(s.removeWhitespace.toUpperCase(Locale.UK)),
            _.value
          )
          .verifying("error.tooLong", c => !(c.value.length > 9))
          .verifying("error.tooShort", c => !(c.value.length < 9))
          .verifying("error.pattern", _.value.forall(_.isLetterOrDigit))
      )(identity)(Some(_))
    )

}
