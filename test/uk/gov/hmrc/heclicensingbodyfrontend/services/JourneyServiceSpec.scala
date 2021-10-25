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

import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.{ControllerSpec, SessionSupport, routes}
import uk.gov.hmrc.heclicensingbodyfrontend.models.EntityType.{Company, Individual}
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models._
import uk.gov.hmrc.heclicensingbodyfrontend.models.ids.CRN
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.util.TimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class JourneyServiceSpec extends ControllerSpec with SessionSupport {

  def requestWithSessionData(s: HECSession): RequestWithSessionData[_] = RequestWithSessionData(FakeRequest(), s)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val appConfig         = instanceOf[AppConfig]
  val journeyService             = new JourneyServiceImpl(mockSessionStore)

  val hecTaxCheckCode                = HECTaxCheckCode("ABC DEF 123")
  val dateOfBirth                    = DateOfBirth(LocalDate.of(1922, 12, 1))
  val dateTimeChecked: ZonedDateTime = TimeUtils.now()
  val taxCheckMatchRequest           =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.DriverOfTaxisAndPrivateHires, Right(dateOfBirth))
  val userAnswersWithAllAnswers      = UserAnswers(
    Some(hecTaxCheckCode),
    Some(LicenceType.DriverOfTaxisAndPrivateHires),
    Some(Individual),
    Some(dateOfBirth),
    None
  )

  val taxCheckMatchCompanyRequest =
    HECTaxCheckMatchRequest(hecTaxCheckCode, LicenceType.OperatorOfPrivateHireVehicles, Left(CRN("SS123456")))

  val userAnswersForCompany = UserAnswers(
    Some(hecTaxCheckCode),
    Some(LicenceType.OperatorOfPrivateHireVehicles),
    Some(Company),
    None,
    Some(CRN("SS123456"))
  )

  "JourneyServiceImpl" when {

    "handling calls to 'firstPage'" must {

      "return the correct call" in {
        journeyService.firstPage shouldBe routes.HECTaxCheckCodeController.hecTaxCheckCode()
      }

    }

    "handling calls to 'updateAndNext'" must {

      "return an error" when {

        "the next page cannot be determined" in {
          val session                                     = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))), None)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCodeSubmit(),
            session
          )

          await(result.value) shouldBe a[Left[_, _]]
        }

        "there is an error updating the session" in {
          val currentSession                              = HECSession(UserAnswers.empty, None)
          val updatedSession                              = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))), None)
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

      "try to return the correct next page" when afterWord("the current page is") {

        "the tax check code page" in {
          val currentSession                              = HECSession(UserAnswers.empty, None)
          val updatedSession                              = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))), None)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(currentSession)

          mockStoreSession(updatedSession)(Right(()))

          val result = journeyService.updateAndNext(
            routes.HECTaxCheckCodeController.hecTaxCheckCode(),
            updatedSession
          )
          await(result.value) shouldBe Right(routes.LicenceTypeController.licenceType())
        }

        "the licence Type page" when {

          "there is no licence type in session" in {
            val session                                     = HECSession(UserAnswers.empty, None)
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(session)

            assertThrows[RuntimeException] {
              journeyService.updateAndNext(
                routes.LicenceTypeController.licenceType(),
                session
              )
            }
          }

          "the licence Type in the session is 'Driver Of Taxis And Private Hires'" in {
            val currentSession                              = HECSession(UserAnswers.empty, None)
            val updatedSession                              = HECSession(
              UserAnswers.empty.copy(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires)
              ),
              None
            )
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(currentSession)

            mockStoreSession(updatedSession)(Right(()))

            val result = journeyService.updateAndNext(
              routes.LicenceTypeController.licenceType(),
              updatedSession
            )
            await(result.value) shouldBe Right(routes.DateOfBirthController.dateOfBirth())
          }

          "the licence Type in the session is other than 'Driver Of Taxis And Private Hires'" in {
            List(
              LicenceType.OperatorOfPrivateHireVehicles,
              LicenceType.ScrapMetalDealerSite,
              LicenceType.ScrapMetalMobileCollector
            ).foreach { licenceType =>
              withClue(s"For licence type $licenceType: ") {
                val answers        = UserAnswers.empty.copy(taxCheckCode = Some(hecTaxCheckCode))
                val session        = HECSession(UserAnswers.empty, None)
                val updatedSession =
                  HECSession(
                    answers.copy(
                      licenceType = Some(LicenceType.ScrapMetalDealerSite)
                    ),
                    None
                  )

                implicit val request: RequestWithSessionData[_] =
                  requestWithSessionData(session)

                mockStoreSession(updatedSession)(Right(()))

                val result = journeyService.updateAndNext(
                  routes.LicenceTypeController.licenceType(),
                  updatedSession
                )
                await(result.value) shouldBe Right(routes.EntityTypeController.entityType())
              }
            }
          }
        }

        "the entity type page" when {

          def test(entityType: EntityType, nextCall: Call) = {
            val currentSession                              = HECSession(UserAnswers.empty, None)
            val updatedSession                              = HECSession(
              UserAnswers(
                taxCheckCode = Some(hecTaxCheckCode),
                licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires),
                entityType = Some(entityType),
                None,
                None
              ),
              None
            )
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(currentSession)

            mockStoreSession(updatedSession)(Right(()))

            val result = journeyService.updateAndNext(
              routes.EntityTypeController.entityType(),
              updatedSession
            )
            await(result.value) shouldBe Right(nextCall)
          }

          "there is no entity type in session" in {
            val session                                     = HECSession(UserAnswers.empty, None)
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(session)

            assertThrows[RuntimeException] {
              journeyService.updateAndNext(
                routes.EntityTypeController.entityType(),
                session
              )
            }
          }

          "the entity type in the session is Individual" in {
            test(EntityType.Individual, routes.DateOfBirthController.dateOfBirth())
          }

          "the entity type in the session is Company" in {
            test(EntityType.Company, routes.CRNController.companyRegistrationNumber())
          }
        }

        "date of birth page " when {

          def nextPageTest(updatedHecSession: HECSession, nextCall: Call) = {
            val currentSession                              = HECSession(UserAnswers.empty, None)
            val updatedSession                              = updatedHecSession
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(currentSession)

            mockStoreSession(updatedSession)(Right(()))

            val result = journeyService.updateAndNext(
              routes.DateOfBirthController.dateOfBirth(),
              updatedSession
            )
            await(result.value) shouldBe Right(nextCall)
          }

          "there is no tax check match result in session" in {
            val session =
              HECSession(userAnswersWithAllAnswers, None)

            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(session)

            assertThrows[RuntimeException] {
              journeyService.updateAndNext(
                routes.DateOfBirthController.dateOfBirth(),
                session
              )
            }
          }

          "the individual details are a match" in {
            nextPageTest(
              HECSession(
                userAnswersWithAllAnswers,
                Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
              ),
              routes.TaxCheckResultController.taxCheckMatch()
            )

          }

          "the individual details are a match but the tax check code has expired" in {

            nextPageTest(
              HECSession(
                userAnswersWithAllAnswers,
                Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired))
              ),
              routes.TaxCheckResultController.taxCheckExpired()
            )

          }

          "the individual details are not a match" when {

            "the verification attempts in session  lower than the max value" in {
              nextPageTest(
                HECSession(
                  userAnswersWithAllAnswers,
                  Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> 1)
                ),
                routes.TaxCheckResultController.taxCheckNotMatch()
              )
            }

            "the verification attempts in session  equal to max value" in {
              nextPageTest(
                HECSession(
                  userAnswersWithAllAnswers,
                  Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch)),
                  verificationAttempts = Map(hecTaxCheckCode.value -> appConfig.maxVerificationAttempts)
                ),
                routes.TaxCheckResultController.tooManyVerificationAttempts()
              )
            }

          }

        }

        "company registration number page " when {

          def nextPageTest(updatedHecSession: HECSession, nextCall: Call) = {
            val currentSession                              = HECSession(UserAnswers.empty, None)
            val updatedSession                              = updatedHecSession
            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(currentSession)

            mockStoreSession(updatedSession)(Right(()))

            val result = journeyService.updateAndNext(
              routes.CRNController.companyRegistrationNumber(),
              updatedSession
            )
            await(result.value) shouldBe Right(nextCall)
          }

          "there is no tax check match result in session" in {
            val session =
              HECSession(userAnswersForCompany, None)

            implicit val request: RequestWithSessionData[_] =
              requestWithSessionData(session)

            assertThrows[RuntimeException] {
              journeyService.updateAndNext(
                routes.CRNController.companyRegistrationNumber(),
                session
              )
            }
          }

          "the company details are a match" in {
            nextPageTest(
              HECSession(
                userAnswersForCompany,
                Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, Match))
              ),
              routes.TaxCheckResultController.taxCheckMatch()
            )

          }

          "the company details are a match but the tax check code has expired" in {

            nextPageTest(
              HECSession(
                userAnswersForCompany,
                Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, Expired))
              ),
              routes.TaxCheckResultController.taxCheckExpired()
            )

          }

          "the company details are not a match" in {

            nextPageTest(
              HECSession(
                userAnswersForCompany,
                Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, NoMatch))
              ),
              routes.TaxCheckResultController.taxCheckNotMatch()
            )

          }

        }

      }

      "not update the session" when {

        "the current session and the updated session are the same" in {
          val session                                     = HECSession(UserAnswers.empty.copy(taxCheckCode = Some(HECTaxCheckCode(""))), None)
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
          val session                                     = HECSession(UserAnswers.empty, None)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.StartController.start()
          )

          result shouldBe routes.StartController.start()
        }

        "the tax check code page" in {
          val session                                     = HECSession(UserAnswers.empty, None)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.HECTaxCheckCodeController.hecTaxCheckCode()
          )

          result shouldBe routes.StartController.start()
        }

        "the licence type page" in {
          val session                                     = HECSession(UserAnswers.empty, None)
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.LicenceTypeController.licenceType()
          )

          result shouldBe routes.HECTaxCheckCodeController.hecTaxCheckCode()
        }

        "the date of birth page via the licence type page" in {
          val session                                     = HECSession(
            UserAnswers(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.DriverOfTaxisAndPrivateHires),
              entityType = None,
              dateOfBirth = None,
              None
            ),
            None
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.DateOfBirthController.dateOfBirth()
          )

          result shouldBe routes.LicenceTypeController.licenceType()
        }

        "the date of birth page via the entity type page" in {
          val session                                     = HECSession(
            UserAnswers(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.ScrapMetalMobileCollector),
              entityType = Some(EntityType.Individual),
              None,
              None
            ),
            None
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.DateOfBirthController.dateOfBirth()
          )

          result shouldBe routes.EntityTypeController.entityType()
        }

        "the entity type page" in {
          List(
            LicenceType.ScrapMetalDealerSite,
            LicenceType.ScrapMetalMobileCollector,
            LicenceType.OperatorOfPrivateHireVehicles
          ).foreach { licenceType =>
            withClue(s"for licence type $licenceType: ") {
              val session                                     = HECSession(
                UserAnswers(
                  taxCheckCode = Some(hecTaxCheckCode),
                  licenceType = Some(licenceType),
                  entityType = None,
                  None,
                  None
                ),
                None
              )
              implicit val request: RequestWithSessionData[_] =
                requestWithSessionData(session)

              val result = journeyService.previous(
                routes.EntityTypeController.entityType()
              )

              result shouldBe routes.LicenceTypeController.licenceType()
            }
          }
        }

        "the CRN page" in {
          val session                                     = HECSession(
            UserAnswers(
              taxCheckCode = Some(hecTaxCheckCode),
              licenceType = Some(LicenceType.ScrapMetalMobileCollector),
              entityType = Some(EntityType.Company),
              None,
              None
            ),
            None
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.CRNController.companyRegistrationNumber()
          )

          result shouldBe routes.EntityTypeController.entityType()
        }

        "Tax Check Code not match page via date of birth page " in {
          val session                                     = HECSession(
            userAnswersWithAllAnswers,
            Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, NoMatch))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckNotMatch()
          )

          result shouldBe routes.DateOfBirthController.dateOfBirth()
        }

        "Tax Check Code not match page via CRN page " in {
          val session                                     = HECSession(
            userAnswersForCompany,
            Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, NoMatch))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckNotMatch()
          )

          result shouldBe routes.CRNController.companyRegistrationNumber()
        }

        "Tax Check Code expired page via DOB page" in {
          val session                                     = HECSession(
            userAnswersWithAllAnswers,
            Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Expired))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckExpired()
          )

          result shouldBe routes.DateOfBirthController.dateOfBirth()
        }

        "Tax Check Code expired page via CRN page" in {
          val session                                     = HECSession(
            userAnswersForCompany,
            Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, Expired))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckExpired()
          )

          result shouldBe routes.CRNController.companyRegistrationNumber()
        }

        "Tax Check Code valid page via DOB page" in {
          val session                                     = HECSession(
            userAnswersWithAllAnswers,
            Some(HECTaxCheckMatchResult(taxCheckMatchRequest, dateTimeChecked, Match))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckMatch()
          )

          result shouldBe routes.DateOfBirthController.dateOfBirth()
        }

        "Tax Check Code valid page via CRN page" in {
          val session                                     = HECSession(
            userAnswersForCompany,
            Some(HECTaxCheckMatchResult(taxCheckMatchCompanyRequest, dateTimeChecked, Match))
          )
          implicit val request: RequestWithSessionData[_] =
            requestWithSessionData(session)

          val result = journeyService.previous(
            routes.TaxCheckResultController.taxCheckMatch()
          )

          result shouldBe routes.CRNController.companyRegistrationNumber()
        }

      }

    }

  }

}
