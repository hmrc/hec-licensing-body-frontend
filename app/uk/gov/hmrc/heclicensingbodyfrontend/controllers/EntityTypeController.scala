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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, of}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.EntityTypeController.{entityTypeForm, entityTypes}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.SessionDataAction
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.{Company, Individual}
import uk.gov.hmrc.heclicensingbodyfrontend.services.JourneyService
import uk.gov.hmrc.heclicensingbodyfrontend.util.{FormUtils, Logging}
import uk.gov.hmrc.heclicensingbodyfrontend.views.html
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EntityTypeController @Inject() (
  sessionDataAction: SessionDataAction,
  journeyService: JourneyService,
  entityTypePage: html.EntityType,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with Logging
    with I18nSupport {

  val entityType: Action[AnyContent] = sessionDataAction { implicit request =>
    val back       = journeyService.previous(routes.EntityTypeController.entityType)
    val entityType = request.sessionData.userAnswers.entityType
    val form       = {
      val emptyForm = entityTypeForm(entityTypes)
      entityType.fold(emptyForm)(emptyForm.fill)
    }

    Ok(entityTypePage(form, back, entityTypes))
  }

  val entityTypeSubmit: Action[AnyContent] = sessionDataAction.async { implicit request =>
    def handleValidEntityType(entityType: EntityType): Future[Result] = {
      val updatedAnswers = request.sessionData.userAnswers.copy(entityType = Some(entityType))
      journeyService
        .updateAndNext(
          routes.EntityTypeController.entityType,
          request.sessionData.copy(userAnswers = updatedAnswers)
        )
        .fold(
          _.doThrow("Could not update session and proceed"),
          Redirect
        )
    }

    entityTypeForm(entityTypes)
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Ok(
            entityTypePage(
              formWithErrors,
              journeyService.previous(routes.EntityTypeController.entityType),
              entityTypes
            )
          ),
        handleValidEntityType
      )
  }

}

object EntityTypeController {

  def entityTypeForm(options: List[EntityType]): Form[EntityType] =
    Form(
      mapping(
        "entityType" -> of(FormUtils.radioFormFormatter(options))
      )(identity)(Some(_))
    )
  val entityTypes: List[EntityType]                               = List(Individual, Company)
}
