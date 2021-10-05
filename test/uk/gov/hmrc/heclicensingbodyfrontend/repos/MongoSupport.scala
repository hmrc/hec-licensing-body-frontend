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

import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.{MongoComponent, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

trait MongoSupport extends MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll { this: Suite with Matchers ⇒

  private def newMongoComponent(): MongoComponent = MongoComponent(mongoUri)

  val mongoComponent: MongoComponent =
    newMongoComponent()

  abstract override def beforeEach(): Unit = {
    super.beforeEach()
    await(mongo().drop())
  }

  abstract override def afterAll(): Unit = {
    super.afterAll()
    mongoComponent.client.close()
  }

}
