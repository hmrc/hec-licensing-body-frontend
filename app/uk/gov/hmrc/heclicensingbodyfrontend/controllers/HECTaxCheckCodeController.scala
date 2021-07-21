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

import com.google.inject.Inject
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.data.Forms.nonEmptyText
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckCode
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.heclicensingbodyfrontend.util.StringUtils.StringOps
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.heclicensingbodyfrontend.views.html

class HECTaxCheckCodeController @Inject() (
  sessionDataAction: SessionDataAction,
  hecTaxCheckCodePage: html.HECTaxCheckCode,
  mcc: MessagesControllerComponents
) extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  import HECTaxCheckCodeController._

  val hecTaxCheckCode: Action[AnyContent] = sessionDataAction { implicit request =>
    val taxCheckCode = request.sessionData.userAnswers.fold(_.taxCheckCode, c => Some(c.taxCheckCode))
    val form         = taxCheckCode.fold(taxCheckCodeForm)(taxCheckCodeForm.fill)
    Ok(hecTaxCheckCodePage(form))
  }

  val hecTaxCheckCodeSubmit: Action[AnyContent] = sessionDataAction { implicit request =>
    taxCheckCodeForm
      .bindFromRequest()
      .fold(
        formWithErrors => Ok(hecTaxCheckCodePage(formWithErrors)),
        taxCheckCode => Ok(s"$taxCheckCode is valid!")
      )
  }

}

object HECTaxCheckCodeController {

  val taxCheckCodeForm: Form[HECTaxCheckCode] = Form(
    nonEmptyText
      .transform[HECTaxCheckCode](s => HECTaxCheckCode(s.removeWhitespace), _.value)
      .verifying("error.tooLong", _.value.length > 9)
      .verifying("error.tooShort", _.value.length < 9)
      .verifying("error.pattern", _.value.exists(!_.isLetterOrDigit))
  )

}
