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

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.connectors.HECConnector
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckMatchResult._
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, Error, HECTaxCheckCode, HECTaxCheckMatchRequest, HECTaxCheckMatchResult}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.{LocalDate, ZoneId, ZonedDateTime}

import scala.concurrent.ExecutionContext.Implicits.global

class HECTaxMatchServiceSpec extends AnyWordSpec with Matchers with MockFactory {

  val mockHecConnector = mock[HECConnector]
  val taxCheckService  = new HECTaxMatchServiceImpl(mockHecConnector)

  def mockMatchTaxCheck(taxCheckMatchRequest: HECTaxCheckMatchRequest)(result: Either[Error, HttpResponse]) =
    (mockHecConnector
      .matchTaxCheck(_: HECTaxCheckMatchRequest)(_: HeaderCarrier))
      .expects(taxCheckMatchRequest, *)
      .returning(EitherT.fromEither(result))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val emptyHeaders = Map.empty[String, Seq[String]]

  val hecTaxCheckCode                = HECTaxCheckCode("ABC DEF 123")
  val dateOfBirth                    = DateOfBirth(LocalDate.of(1922, 12, 1))
  val dateTimeChecked: ZonedDateTime = ZonedDateTime.of(2021, 9, 10, 8, 2, 0, 0, ZoneId.of("Europe/London"))

  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(dateOfBirth))

  val taxCheckMatchResult: HECTaxCheckMatchResult   = Match(taxCheckMatchRequest, dateTimeChecked)
  val taxCheckNoMatchResult: HECTaxCheckMatchResult = NoMatch(taxCheckMatchRequest, dateTimeChecked)
  val taxCheckExpiredResult: HECTaxCheckMatchResult = Expired(taxCheckMatchRequest, dateTimeChecked)

  val taxCheckMatchResultJson   = Json.toJson(taxCheckMatchResult)
  val taxCheckNoMatchResultJson = Json.toJson(taxCheckNoMatchResult)
  val taxCheckExpiredResultJson = Json.toJson(taxCheckExpiredResult)

  "HECTaxMatchServiceImpl" when {

    "handling request to match tax check code against database" must {

      "return an error" when {

        "the http call fails" in {
          mockMatchTaxCheck(taxCheckMatchRequest)(Left(Error("")))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe a[Left[_, _]]
        }

        "the http response does come back with a non-ok (200) response" in {

          mockMatchTaxCheck(taxCheckMatchRequest)(Right(HttpResponse(ACCEPTED, taxCheckMatchResultJson, emptyHeaders)))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe a[Left[_, _]]
        }

        "there is no json in the response" in {
          mockMatchTaxCheck(taxCheckMatchRequest)(Right(HttpResponse(OK, "hi")))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe a[Left[_, _]]
        }

      }

      "return successfully" when {

        "the tax check is a match and the json response can be parsed" in {
          mockMatchTaxCheck(taxCheckMatchRequest)(Right(HttpResponse(OK, taxCheckMatchResultJson, emptyHeaders)))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe Right(taxCheckMatchResult)
        }

        "the tax check is a No match and the json response can be parsed" in {
          mockMatchTaxCheck(taxCheckMatchRequest)(Right(HttpResponse(OK, taxCheckNoMatchResultJson, emptyHeaders)))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe Right(taxCheckNoMatchResult)
        }

        "the tax check code is Expired and the json response can be parsed" in {
          mockMatchTaxCheck(taxCheckMatchRequest)(Right(HttpResponse(OK, taxCheckExpiredResultJson, emptyHeaders)))

          val result = taxCheckService.matchTaxCheck(taxCheckMatchRequest)
          await(result.value) shouldBe Right(taxCheckExpiredResult)
        }

      }
    }

  }

}
