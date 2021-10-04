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

package uk.gov.hmrc.heclicensingbodyfrontend.repos

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.heclicensingbodyfrontend.models.{Error, HECSession}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SessionStoreImpl])
trait SessionStore {

  def get()(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, Option[HECSession]]

  def store(sessionData: HECSession)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, Unit]

}

@Singleton
class SessionStoreImpl @Inject() (
  mongo: MongoComponent,
  configuration: Configuration
)(implicit
  ec: ExecutionContext
) extends SessionCacheRepository(
      mongoComponent = mongo,
      collectionName = "sessions",
      ttl = configuration.get[FiniteDuration]("session-store.ttl"),
      timestampSupport = new CurrentTimestampSupport(),
      sessionIdKey = "hec-session"
    )
    with SessionStore {

  val sessionKey: String = "hec-session"

  def get()(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, Option[HECSession]] =
    hc.sessionId.map(_.value) match {
      case Some(_) ⇒
        EitherT(doGet[HECSession]())
      case None =>
        EitherT.leftT(Error("no session id found in headers - cannot query mongo"))
    }

  def store(
    sessionData: HECSession
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit] =
    hc.sessionId.map(_.value) match {
      case Some(_) ⇒ EitherT(doStore(sessionData))
      case None ⇒
        EitherT.leftT(
          Error("no session id found in headers - cannot store data in mongo")
        )
    }

  private def doGet[A : Reads]()(implicit
    ec: ExecutionContext,
    request: Request[_]
  ): Future[Either[Error, Option[A]]] = preservingMdc {
    getFromSession[A](DataKey(sessionKey))
      .map(Right(_))
      .recover { case e ⇒ Left(Error(e)) }
  }

  private def doStore[A : Writes](a: A)(implicit
    ec: ExecutionContext,
    request: Request[_]
  ): Future[Either[Error, Unit]] =
    preservingMdc {
      putSession[A](DataKey(sessionKey), a)
        .map(_ => Right(()))
        .recover { case e => Left(Error(e)) }
    }
}
