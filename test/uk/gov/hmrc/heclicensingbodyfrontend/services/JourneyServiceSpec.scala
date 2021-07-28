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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.{SessionSupport, routes}
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession, HECTaxCheckCode, UserAnswers}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class JourneyServiceSpec extends AnyWordSpec with Matchers with MockFactory with SessionSupport {

  val journeyService = new JourneyServiceImpl(mockSessionStore)

  def requestWithSessionData(s: HECSession): RequestWithSessionData[_] = RequestWithSessionData(FakeRequest(), s)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "JourneyServiceImpl" when {

    "handling calls to 'firstPage'" must {

      "return the correct call" in {
        journeyService.firstPage shouldBe routes.HECTaxCheckCodeController.hecTaxCheckCode()
      }

    }

    "handling calls to 'updateAndNext'" must {

      "return an error" when {

        "the next page cannot be determined" in {
          val session                                     = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))))
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCodeSubmit(),
            session
          )

          await(result.value) shouldBe a[Left[_, _]]
        }

        "there is an error updating the session" in {
          val currentSession                              = HECSession(UserAnswers.empty)
          val updatedSession                              = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))))
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(currentSession)

          mockStoreSession(updatedSession)(Left(Error("")))

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCode(),
            updatedSession
          )
          await(result.value) shouldBe a[Left[_, _]]
        }

      }

      "return the correct next page" when afterWord("the current page is") {

        "the tax check code page" in {
          val currentSession                              = HECSession(UserAnswers.empty)
          val updatedSession                              = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))))
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(currentSession)

          mockStoreSession(updatedSession)(Right(()))

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCode(),
            updatedSession
          )
          await(result.value) shouldBe Right(routes.LicenceTypeController.licenceType())
        }

      }

      "not update the session" when {

        "the current session and the updated session are the same" in {
          val session                                     = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))))
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCode(),
            session
          )
          await(result.value) shouldBe Right(routes.LicenceTypeController.licenceType())

        }

      }

    }

    "handling calls to 'previous'" must {

      "return the correct previous page" when afterWord("the current page is") {

        "the start endpoint" in {
          val session                                     = HECSession(UserAnswers.empty)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.StartController.start()
          )

          result shouldBe routes.StartController.start()
        }

        "the tax check code page" in {
          val session                                     = HECSession(UserAnswers.empty)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.HECTaxCheckCodeController.hecTaxCheckCode()
          )

          result shouldBe routes.StartController.start()
        }

      }

    }

  }

}