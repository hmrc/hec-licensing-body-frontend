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

import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession}
import uk.gov.hmrc.heclicensingbodyfrontend.models.UserAnswers.IncompleteUserAnswers
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore

import scala.concurrent.ExecutionContext.Implicits.global

class StartControllerSpec extends ControllerSpec with SessionSupport {

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore)
    )

  val controller = instanceOf[StartController]

  "StartController" when {

    "handling requests to start" must {

      def performAction() = controller.start(FakeRequest())

      val newSession = HECSession(IncompleteUserAnswers.empty)

      "show an error page" when {

        "there is an error storing a new session" in {
          mockStoreSession(newSession)(Left(Error("")))

          status(performAction()) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "redirect to the first page of the journey" when {

        "a new session has been created and stored for the session" in {
          mockStoreSession(newSession)(Right(()))

          checkIsRedirect(performAction(), routes.ApplicantDetailsController.applicantType())
        }

      }

    }

  }

}