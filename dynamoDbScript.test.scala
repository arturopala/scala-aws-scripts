//> using scala 3.3.0
//> using jvm 17
//> using dep org.apache.logging.log4j:log4j-slf4j-impl:2.20.0
//> using toolkit latest

import munit.Clue.generate
import scala.concurrent.duration.Duration

class DynameDbPutItemTest extends munit.FunSuite {

  override val munitTimeout = Duration(300, "s")

  test("build and run native script on a localstack") {

    val localstackPort = 4566

    os.remove(os.pwd / "dynamoDbScript")
    os.remove(os.pwd / "dynamoDbScript.build_artifacts.txt")

    val buildNativeImage =
      os.proc(
        "scala-cli",
        "--power",
        "package",
        "--native-image",
        "dynamoDbScript.sc",
        "--",
        "--no-fallback",
        "--initialize-at-build-time=org.slf4j"
      ).call(
        timeout = Long.MaxValue
      )

    val localstackUp =
      os.proc("docker", "compose", "-f", "docker/localstack.yml", "up")
        .spawn(env =
          Map(
            "LOCALSTACK_PORT" -> localstackPort.toString
          )
        )

    val script =
      os.proc("./dynamoDbScript", "-s", "alpha")
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
