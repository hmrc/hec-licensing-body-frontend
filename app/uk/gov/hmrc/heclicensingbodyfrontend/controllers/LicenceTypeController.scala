/*
 * Copyright 2022 HM Revenue & Customs
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
import cats.syntax.eq._
import com.google.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, of}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.LicenceTypeController.{licenceTypeForm, licenceTypeOptions, licenceTypes}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.{RequestWithSessionData, SessionDataAction}
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType.{BookingOffice, DriverOfTaxisAndPrivateHires, OperatorOfPrivateHireVehicles, ScrapMetalDealerSite, ScrapMetalMobileCollector}
import uk.gov.hmrc.heclicensingbodyfrontend.models.views.LicenceTypeOption
import uk.gov.hmrc.heclicensingbodyfrontend.models.{HECSession, UserAnswers}
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.{FormUtils, Logging}
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LicenceTypeController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  licenceTypePage: html.LicenceType,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  val licenceType: Action[AnyContent] = sessionDataAction { implicit request =>
    val back        = journeyService.previous(routes.LicenceTypeController.licenceType)
    val licenceType = request.sessionData.userAnswers.licenceType
    val form = {
      val emptyForm = licenceTypeForm(licenceTypes(request.sessionData.isScotNIPrivateBeta))
      licenceType.fold(emptyForm)(emptyForm.fill)
    }
    val options     = licenceTypeOptions(request.sessionData.isScotNIPrivateBeta)
    Ok(licenceTypePage(form, back, options))
  }

  val licenceTypeSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    licenceTypeForm(licenceTypes(request.sessionData.isScotNIPrivateBeta))
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Ok(
            licenceTypePage(
              formWithErrors,
              journeyService.previous(routes.LicenceTypeController.licenceType),
              licenceTypeOptions(request.sessionData.isScotNIPrivateBeta)
            )
          ),
        handleValidLicenceType
      )
  }

  private def handleValidLicenceType(
    licenceType: LicenceType
  )(implicit request: RequestWithSessionData[_]): Future[Result] = {

    val updatedAnswers: UserAnswers = request.sessionData.userAnswers.copy(licenceType = Some(licenceType))
    val updatedSession: HECSession  = request.sessionData.copy(userAnswers = updatedAnswers)

    journeyService
      .updateAndNext(routes.LicenceTypeController.licenceType, updatedSession)
      .fold(
        _.doThrow("Could not update session and proceed"),
        Redirect
      )
  }

}

object LicenceTypeController {

  def licenceTypes(isScotNIPrivateBeta: Option[Boolean]): List[LicenceType] = {
    val licences = List(
      DriverOfTaxisAndPrivateHires,
      OperatorOfPrivateHireVehicles,
      BookingOffice,
      ScrapMetalMobileCollector,
      ScrapMetalDealerSite
    )
    if (isScotNIPrivateBeta.contains(true)) licences else licences.filterNot(_ === BookingOffice)
  }

  def licenceTypeOptions(isScotNIPrivateBeta: Option[Boolean]): List[LicenceTypeOption] =
    licenceTypes(isScotNIPrivateBeta).map(LicenceTypeOption.licenceTypeOption(_, isScotNIPrivateBeta))

  def licenceTypeForm(options: List[LicenceType]): Form[LicenceType] =
    Form(
      mapping(
        "licenceType" -> of(FormUtils.radioFormFormatter(options))
      )(identity)(Some(_))
    )
}
