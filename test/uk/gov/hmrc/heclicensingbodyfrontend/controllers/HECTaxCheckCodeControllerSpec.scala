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
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECSession
import uk.gov.hmrc.heclicensingbodyfrontend.models.UserAnswers.IncompleteUserAnswers
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class HECTaxCheckCodeControllerSpec extends ControllerSpec with SessionSupport with SessionDataActionBehaviour {

  override val overrideBindings =
    List[GuiceableModule](
      bind[SessionStore].toInstance(mockSessionStore)
    )

  val controller = instanceOf[HECTaxCheckCodeController]

  "HECTaxCheckCodeController" when {

    "handling requests to display the applicant type page" must {

      def performAction(): Future[Result] = controller.hecTaxCheckCode(FakeRequest())

      behave like sessionDataActionBehaviour(performAction)

      "show the page" when {

        "session data is found" in {
          mockGetSession(HECSession(IncompleteUserAnswers.empty))

          val result = performAction()
          status(result) shouldBe OK
        }

      }

    }

  }

}
