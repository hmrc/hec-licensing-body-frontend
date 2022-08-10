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

package uk.gov.hmrc.heclicensingbodyfrontend.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.hmrcfrontend.config.ContactFrontendConfig

@Singleton
class AppConfig @Inject() (config: Configuration, contactFrontendConfig: ContactFrontendConfig) {

  val platformHost: Option[String] = config.getOptional[String]("platform.frontend.host")

  val contactFrontendUrl: String =
    contactFrontendConfig.baseUrl.getOrElse(sys.error("Could not find config for contact frontend url"))

  val contactFormServiceIdentifier: String =
    contactFrontendConfig.serviceId.getOrElse(sys.error("Could not find config for contact frontend service id"))

  val betaFeedbackUrl: String = s"$contactFrontendUrl/contact/beta-feedback?service=$contactFormServiceIdentifier"

  val taxCheckGuidanceUrl: String = config.get[String]("external-url.tax-check-guidance")

  val licencingBodyStartUrl: String = config.get[String]("external-url.licencing-body-start")

  val companiesHouseSearchUrl: String = config.get[String]("external-url.companies-house-search")

  val maxVerificationAttempts: Int = config.get[Int]("maximum-verification-attempts")

  val verificationAttemptsLockTimeHours: Long = config.get[Long]("tax-check-verification-attempts-lock-hours")

  val exitSurveyUrl: String = {
    val baseUrl = platformHost.getOrElse(config.get[String]("feedback-frontend.base-url"))
    s"$baseUrl/feedback/$contactFormServiceIdentifier"
  }

}
