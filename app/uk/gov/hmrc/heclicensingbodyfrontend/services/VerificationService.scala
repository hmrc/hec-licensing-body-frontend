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

package uk.gov.hmrc.heclicensingbodyfrontend.services

import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject}
import play.api.mvc.Results.{InternalServerError, Redirect}
import play.api.mvc.{Call, Result}
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.models.{HECTaxCheckCode}
import uk.gov.hmrc.heclicensingbodyfrontend.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[VerificationServiceImpl])
trait VerificationService {

  def goToNextPage(call: Call)(implicit
    requestWithSessionData: RequestWithSessionData[_],
    headerCarrier: HeaderCarrier
  ): Future[Result]

  def maxVerificationAttemptReached(hecTaxCheckCode: HECTaxCheckCode)(implicit
    requestWithSessionData: RequestWithSessionData[_]
  ): Boolean

}

class VerificationServiceImpl @Inject() (journeyService: JourneyService)(implicit
  ec: ExecutionContext,
  appConfig: AppConfig
) extends VerificationService
    with Logging {

  override def goToNextPage(call: Call)(implicit
    requestWithSessionData: RequestWithSessionData[_],
    headerCarrier: HeaderCarrier
  ): Future[Result] =
    journeyService
      .updateAndNext(
        call,
        requestWithSessionData.sessionData
      )
      .fold(
        { e =>
          logger.logger.warn("Could not update session and proceed", e)
          InternalServerError
        },
        Redirect
      )

  override def maxVerificationAttemptReached(
    hecTaxCheckCode: HECTaxCheckCode
  )(implicit requestWithSessionData: RequestWithSessionData[_]): Boolean =
    requestWithSessionData.sessionData.verificationAttempts
      .get(hecTaxCheckCode)
      .exists(_ >= appConfig.maxVerificationAttempts)

}
