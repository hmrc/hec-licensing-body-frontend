package uk.gov.hmrc.heclicensingbodyfrontend.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsString, Json}

class EntityTypeSpec extends AnyWordSpec with Matchers {

  "EntityType" should {

    "write to JSON" when {

      s"the entity type is ${EntityType.Company}" in {
        Json.toJson[EntityType](EntityType.Company) shouldBe JsString("Company")
      }

      s"the entity type is ${EntityType.Individual}" in {
        Json.toJson[EntityType](EntityType.Individual) shouldBe JsString("Individual")
      }
    }

    "read from JSON" when {

      s"the entity type is ${EntityType.Company}" in {
        JsString("Company").as[EntityType] shouldBe EntityType.Company
      }

      s"the entity type is ${EntityType.Individual}" in {
        JsString("Individual").as[EntityType] shouldBe EntityType.Individual
      }
    }

    "fail to read from JSON" when {

      val js = JsString("aaaaaaa")

      s"the entity type is not recognised" in {
        js.validate[EntityType] shouldBe JsError(s"Unknown entity type: ${js.toString()}")

      }
    }
  }

}
