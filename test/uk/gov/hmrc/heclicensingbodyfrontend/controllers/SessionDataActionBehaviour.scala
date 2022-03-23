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

import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.models.Error

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SessionDataActionBehaviour { this: ControllerSpec with SessionSupport =>

  def sessionDataActionBehaviour(performAction: () => Future[Result]): Unit = {

    "show an error page" when {

      "there is an error getting session data" in {
        mockGetSession(Left(Error("")))

        assertThrows[RuntimeException](await(performAction()))

      }

    }

    "redirect to the start endpoint" when {

      "no session data is found" in {
        mockGetSession(Right(None))

        checkIsRedirect(performAction(), routes.StartController.start)
      }

    }

  }

}
