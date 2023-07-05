#!/usr/bin/env -S scala-cli shebang

//> using scala 3.3.0
//> using jvm 17
//> using dep software.amazon.awssdk:bom:2.20.98
//> using dep software.amazon.awssdk:dynamodb:2.20.98
//> using dep software.amazon.awssdk:sso:2.20.98
//> using file CommandLineUtils.scala
//> using file AWSDynamoDbUtils.scala

import java.util.UUID
import java.time.Instant
import scala.jdk.CollectionConverters.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate
import AWSDynamoDbUtils.{*, given}
import CommandLineUtils.*

val stage = requireScriptParameter('s', "stage")(args)

val region = Region.US_EAST_1

run {
  withDynamoDbClient(region) {

    createTable(
      tableName = s"pets-$stage",
      key = "uuid",
      ignoreTableAlreadyExists = true
    )

    val uuid = UUID.randomUUID().toString()

    updateItemInTable(
      tableName = s"pets-$stage",
      key = "uuid" -> uuid,
      update = Map(
        "name" -> "Rex",
        "dateOfBirth" -> "2020-08-18",
        "sex" -> "M",
        "exampleList" -> listOf("2021-09-18", 7, false),
        "exampleMap" -> mapOf(
          "a" -> Seq("2021-09-18", "2022-09-22"),
          "b" -> "950",
          "c" -> 100,
          "d" -> true,
          "e" -> mapOf(
            "e1" -> "alpha",
            "e2" -> 567
          )
        )
      )
    )

    val maybeItem = getItem(
      tableName = s"pets-$stage",
      key = "uuid" -> uuid
    )
  }

}
