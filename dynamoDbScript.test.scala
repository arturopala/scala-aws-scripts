//> using scala 3.3.0
//> using jvm 17
//> using dep org.apache.logging.log4j:log4j-slf4j-impl:2.20.0
//> using toolkit latest

import munit.Clue.generate

class DynameDbPutItemTest extends munit.FunSuite {
  test("run script on a localstack") {

    val localstackPort = 4566

    val localstackUp =
      os.proc("docker", "compose", "-f", "docker/localstack.yml", "up")
        .spawn(env =
          Map(
            "LOCALSTACK_PORT" -> localstackPort.toString
          )
        )

    val script =
      os.proc("./dynamoDbScript.sc", "-s", "alpha")
        .call(
          check = false,
          env = Map(
            "AWS_PROFILE" -> "default",
            "AWS_ACCESS_KEY_ID" -> "test",
            "AWS_SECRET_ACCESS_KEY" -> "test",
            "AWS_DEFAULT_REGION" -> "us-east-1",
            "LOCALSTACK_PORT" -> localstackPort.toString
          )
        )

    assert(script.exitCode == 0)

    val localstackDown =
      os.proc("docker", "compose", "-f", "docker/localstack.yml", "down")
        .call(env =
          Map(
            "LOCALSTACK_PORT" -> localstackPort.toString
          )
        )

  }
}
