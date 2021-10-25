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

import cats.Eq
import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.mvc.Call
import uk.gov.hmrc.heclicensingbodyfrontend.config.AppConfig
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.actions.RequestWithSessionData
import uk.gov.hmrc.heclicensingbodyfrontend.controllers.routes
import uk.gov.hmrc.heclicensingbodyfrontend.models.HECTaxCheckStatus._
import uk.gov.hmrc.heclicensingbodyfrontend.models.licence.LicenceType
import uk.gov.hmrc.heclicensingbodyfrontend.models.{EntityType, Error, HECSession, HECTaxCheckMatchResult}
import uk.gov.hmrc.heclicensingbodyfrontend.repos.SessionStore
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[JourneyServiceImpl])
trait JourneyService {

  def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[_],
    hc: HeaderCarrier
  ): EitherT[Future, Error, Call]

  def previous(current: Call)(implicit r: RequestWithSessionData[_]): Call

  def firstPage: Call

}

@Singleton
class JourneyServiceImpl @Inject() (sessionStore: SessionStore)(implicit ex: ExecutionContext, appConfig: AppConfig)
    extends JourneyService {

  implicit val callEq: Eq[Call] = Eq.instance(_.url === _.url)

  // map representing routes from one page to another when users submit answers. The keys are the current page and the
  // values are the destination pages which come after the current page. The destination can sometimes depend
  // on state (e.g. the type of user or the answers users have submitted), hence the value type `HECSession => Call`
  lazy val paths: Map[Call, HECSession => Call] = Map(
    routes.StartController.start()                     -> (_ => firstPage),
    routes.HECTaxCheckCodeController.hecTaxCheckCode() -> (_ => routes.LicenceTypeController.licenceType()),
    routes.LicenceTypeController.licenceType()         -> licenceTypeRoute,
    routes.EntityTypeController.entityType()           -> entityTypeRoute,
    routes.DateOfBirthController.dateOfBirth()         -> dateOfBirthRoute,
    routes.CRNController.companyRegistrationNumber()   -> crnRoute
  )

  lazy val firstPage: Call = routes.HECTaxCheckCodeController.hecTaxCheckCode()

  override def updateAndNext(current: Call, updatedSession: HECSession)(implicit
    r: RequestWithSessionData[_],
    hc: HeaderCarrier
  ): EitherT[Future, Error, Call] =
    paths.get(current).map(_(updatedSession)) match {
      case None       =>
        EitherT.leftT(Error(s"Could not find next for $current"))
      case Some(next) =>
        if (r.sessionData === updatedSession)
          EitherT.pure(next)
        else
          sessionStore.store(updatedSession).map(_ => next)
    }

  override def previous(current: Call)(implicit
    r: RequestWithSessionData[_]
  ): Call = {
    @tailrec
    def loop(previous: Call): Option[Call] =
      paths.get(previous) match {
        case Some(calculateNext) =>
          val next = calculateNext(r.sessionData)
          if (next === current) Some(previous)
          else loop(next)
        case _                   => None
      }

    if (current === routes.StartController.start())
      current
    else
      loop(routes.StartController.start())
        .getOrElse(sys.error(s"Could not find previous for $current"))
  }

  private def licenceTypeRoute(session: HECSession): Call =
    session.userAnswers.licenceType match {
      case None =>
        sys.error("Could not find licence type for licence type route")

      case Some(licenceType) =>
        if (licenceTypeForIndividualAndCompany(licenceType)) routes.EntityTypeController.entityType()
        else routes.DateOfBirthController.dateOfBirth()

    }

  private def licenceTypeForIndividualAndCompany(licenceType: LicenceType): Boolean =
    licenceType =!= LicenceType.DriverOfTaxisAndPrivateHires

  private def entityTypeRoute(session: HECSession): Call =
    session.userAnswers.entityType match {
      case None =>
        sys.error("Could not find entity type for entity type route")

      case Some(EntityType.Individual) =>
        routes.DateOfBirthController.dateOfBirth()

      case Some(EntityType.Company) =>
        routes.CRNController.companyRegistrationNumber()

    }

  private def dateOfBirthRoute(session: HECSession): Call =
    session.taxCheckMatch match {
      case Some(taxMatch) =>
        val currentAttemptMap   = session.verificationAttempts
        val taxCode             = session.userAnswers.taxCheckCode.getOrElse(sys.error("taxCheckCode is not in session"))
        val currentAttemptCount = currentAttemptMap.get(taxCode).getOrElse(0)
        val maxAttemptReached   = currentAttemptCount >= appConfig.maxVerificationAttempts
        if (maxAttemptReached) {
          routes.TaxCheckResultController.tooManyVerificationAttempts()
        } else {
          taxMatch match {
            case HECTaxCheckMatchResult(_, _, Match)   => routes.TaxCheckResultController.taxCheckMatch()
            case HECTaxCheckMatchResult(_, _, Expired) => routes.TaxCheckResultController.taxCheckExpired()
            case HECTaxCheckMatchResult(_, _, NoMatch) => routes.TaxCheckResultController.taxCheckNotMatch()
          }
        }

      case None =>
        sys.error("Could not find tax match result for date of birth route")
    }

  private def crnRoute(session: HECSession): Call =
    session.taxCheckMatch match {
      case Some(taxMatch) =>
        taxMatch match {
          case HECTaxCheckMatchResult(_, _, Match)   => routes.TaxCheckResultController.taxCheckMatch()
          case HECTaxCheckMatchResult(_, _, Expired) => routes.TaxCheckResultController.taxCheckExpired()
          case HECTaxCheckMatchResult(_, _, NoMatch) => routes.TaxCheckResultController.taxCheckNotMatch()
        }

      case None =>
        sys.error("Could not find tax match result for company registration route")
    }

}
