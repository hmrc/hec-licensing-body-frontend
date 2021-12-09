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

package uk.gov.hmrc.heclicensingbodyfrontend.connectors

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.http.HeaderNames
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{DateOfBirth, HECTaxCheckCode, HECTaxCheckMatchRequest}
import uk.gov.hmrc.http.{HeaderCarrier}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class HECConnectorImplSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport with ConnectorSpec {

  val (protocol, host, port) = ("http", "host", "123")

  val config = Configuration(
    ConfigFactory.parseString(s"""
                                 | microservice.services.hec {
                                 |    protocol = "$protocol"
                                 |    host     = "$host"
                                 |    port     = $port
                                 |  }
                                 |
                                 | internal-auth {
                                 |  token = "123456789"
                                 | }
                                 |""".stripMargin)
  )

  val connector                  = new HECConnectorImpl(mockHttp, config)
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val hecTaxCheckCode = HECTaxCheckCode("ABC DEF 123")
  val dateOfBirth     = DateOfBirth(LocalDate.of(1922, 12, 1))

  val taxCheckMatchRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(dateOfBirth))

  "HECConnectorImpl" when {

    "handling requests to get HEC Tax match  Result" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val expectedUrl = s"$protocol://$host:$port/hec/match-tax-check"

      behave like connectorBehaviour(
        mockPost(expectedUrl, Seq(HeaderNames.AUTHORIZATION -> "123456789"), taxCheckMatchRequest)(_),
        () => connector.matchTaxCheck(taxCheckMatchRequest)
      )

    }

  }

}
