import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import scala.jdk.CollectionConverters.*
import scala.io.AnsiColor.*

object CommandLineUtils {

  def optionalScriptParameter(shortName: Char, longName: String)(
      args: Array[String]
  ): Option[String] =
    args.zipWithIndex
      .find((a, i) => a == s"-$shortName" || a == s"--$longName")
      .flatMap((a, i) =>
        if (args.length > i + 1) then
          val value = args(i + 1)
          if (value.startsWith("-")) None
          else Some(value)
        else None
      )
      .orElse {
        val sysValue = System.getProperty(longName)
        if (sysValue != null) then Some(sysValue)
        else
          val envValue = System.getenv(longName.toUpperCase())
          if (envValue != null) then Some(envValue)
          else None
      }

  def requireScriptParameter(shortName: Char, longName: String)(
      args: Array[String]
  ): String =
    optionalScriptParameter(shortName, longName)(args)
      .getOrElse {
        System.err.println(
          s"${RED}Required ${BOLD}-$shortName${RESET}${RED} or ${BOLD}--$longName${RESET}${RED} parameter is missing.${RESET}"
        )
        System.exit(1)
        ""
      }

}
