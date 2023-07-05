import software.amazon.awssdk.core.waiters.WaiterResponse
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.io.AnsiColor.*
import java.net.URI
import scala.util.control.NonFatal

object AWSDynamoDbUtils {

  val INFO = "\u001b[38;5;33m"
  val ERROR = RED
  val KEY = WHITE
  val VALUE = WHITE
  val TYPE = "\u001b[38;5;37m"

  def run(block: => Unit): Unit =
    try (block)
    catch {
      case e: ResourceNotFoundException =>
        System.err.println(
          s"${ERROR}[ERROR] The Amazon DynamoDB table can't be found.\nCause: $e${RESET}"
        )
        System.err.println(
          s"${ERROR}Be sure that it exists and that you've typed its name correctly!${RESET}"
        )
        System.exit(2)

      case NonFatal(e) =>
        System.err.println(s"${ERROR}[ERROR] ${e.getMessage()}${RESET}")
        System.exit(1)
    }

  def withDynamoDbClient(
      region: Region
  )(body: DynamoDbClient ?=> Unit): Unit = {
    val localstackPort = System.getenv("LOCALSTACK_PORT")
    val isLocalstack = localstackPort != null

    val dbClientBuilder = DynamoDbClient
      .builder()
      .credentialsProvider(ProfileCredentialsProvider.create())
      .region(region)

    val dbClient =
      if (isLocalstack)
      then
        dbClientBuilder
          .endpointOverride(URI.create(s"http://localhost:$localstackPort"))
          .build()
      else
        dbClientBuilder
          .build()

    try {
      body(using dbClient)
    } finally {
      dbClient.close()
    }
  }

  def createTable(
      tableName: String,
      key: String,
      ignoreTableAlreadyExists: Boolean = false,
      readCapacityUnits: Int = 1,
      writeCapacityUnits: Int = 1
  )(using ddb: DynamoDbClient): DescribeTableResponse = {
    try {
      val dbWaiter: DynamoDbWaiter = ddb.waiter()
      val request: CreateTableRequest = CreateTableRequest
        .builder()
        .attributeDefinitions(
          AttributeDefinition
            .builder()
            .attributeName(key)
            .attributeType(ScalarAttributeType.S)
            .build()
        )
        .keySchema(
          KeySchemaElement
            .builder()
            .attributeName(key)
            .keyType(KeyType.HASH)
            .build()
        )
        .provisionedThroughput(
          ProvisionedThroughput
            .builder()
            .readCapacityUnits(readCapacityUnits.toLong)
            .writeCapacityUnits(writeCapacityUnits.toLong)
            .build()
        )
        .tableName(tableName)
        .build()

      val response: CreateTableResponse = ddb.createTable(request)
      val tableRequest: DescribeTableRequest = DescribeTableRequest
        .builder()
        .tableName(tableName)
        .build()

      // Wait until the Amazon DynamoDB table is created.
      val waiterResponse: WaiterResponse[DescribeTableResponse] =
        dbWaiter.waitUntilTableExists(tableRequest)

      waiterResponse.matched().response().toScala match {
        case Some(value) => value
        case None =>
          throw new Exception("Didn't receive response from describe table.")
      }

    } catch {
      case e: DynamoDbException =>
        if (
          ignoreTableAlreadyExists
          && e.awsErrorDetails().errorCode() == "ResourceInUseException"
        ) then
          val tableRequest: DescribeTableRequest = DescribeTableRequest
            .builder()
            .tableName(tableName)
            .build()
          ddb.describeTable(tableRequest)
        else throw e
    }
  }

  def putItemInTable(
      tableName: String,
      item: Map[String, AttributeValue]
  )(using DynamoDbClient): PutItemResponse = {
    val request: PutItemRequest = PutItemRequest
      .builder()
      .tableName(tableName)
      .item(item.asJava)
      .build()

    val response: PutItemResponse = summon[DynamoDbClient].putItem(request)
    System.out.println(
      s"${GREEN}Success, ${showMetadata(response.responseMetadata())}${RESET}"
    )
    response
  }

  def updateItemInTable(
      tableName: String,
      key: (String, AttributeValue),
      update: Map[String, AttributeValueUpdate]
  )(using DynamoDbClient): UpdateItemResponse = {
    val request: UpdateItemRequest = UpdateItemRequest
      .builder()
      .tableName(tableName)
      .key(Map(key).asJava)
      .attributeUpdates(update.asJava)
      .build()

    System.out.println(
      s"${INFO}Updating $tableName:\n\t${KEY}${BOLD}${key._1}${RESET}${INFO} = KEY ${showAttributeValue(
          key._2
        )}\n${showMapAttributeValueUpdate(update)}${RESET}"
    )
    val response: UpdateItemResponse =
      summon[DynamoDbClient].updateItem(request)
    System.out.println(
      s"${GREEN}Success, ${showMetadata(response.responseMetadata())}${RESET}"
    )
    response
  }

  def getItem(tableName: String, key: (String, AttributeValue))(using
      DynamoDbClient
  ): Option[Map[String, AttributeValue]] = {

    val request: GetItemRequest = GetItemRequest
      .builder()
      .key(Map(key).asJava)
      .tableName(tableName)
      .build()

    // If there is no matching item, GetItem does not return any data.
    val returnedItem: Map[String, AttributeValue] =
      summon[DynamoDbClient].getItem(request).item().asScala.toMap

    if (returnedItem.isEmpty)
      println(
        s"${ERROR}No item found in $tableName with the key $key!${RESET}"
      )
      None
    else {
      Some(returnedItem)
    }
  }

  def sha256Hash(text: String): String =
    String.format(
      "%064x",
      new java.math.BigInteger(
        1,
        java.security.MessageDigest
          .getInstance("SHA-256")
          .digest(text.getBytes("UTF-8"))
      )
    )

  given Conversion[String, AttributeValue] with
    def apply(value: String): AttributeValue =
      AttributeValue.builder().s(value).build()

  given Conversion[Int, AttributeValue] with
    def apply(value: Int): AttributeValue =
      AttributeValue.builder().n(value.toString()).build()

  given Conversion[Long, AttributeValue] with
    def apply(value: Long): AttributeValue =
      AttributeValue.builder().n(value.toString()).build()

  given Conversion[Boolean, AttributeValue] with
    def apply(value: Boolean): AttributeValue =
      AttributeValue.builder().bool(value).build()

  given [T](using
      Conversion[T, AttributeValue]
  ): Conversion[Map[String, T], AttributeValue] with
    def apply(value: Map[String, T]): AttributeValue =
      AttributeValue
        .builder()
        .m(
          value
            .mapValues(summon[Conversion[T, AttributeValue]])
            .toMap
            .asJava
        )
        .build()

  given [T](using
      Conversion[T, AttributeValue]
  ): Conversion[Iterable[T], AttributeValue] with
    def apply(value: Iterable[T]): AttributeValue =
      AttributeValue
        .builder()
        .l(
          value
            .map(summon[Conversion[T, AttributeValue]])
            .toSeq
            .asJava
        )
        .build()

  given [T](using
      Conversion[T, AttributeValue]
  ): Conversion[T, AttributeValueUpdate] with
    def apply(value: T): AttributeValueUpdate =
      AttributeValueUpdate
        .builder()
        .value(value)
        .action(AttributeAction.PUT)
        .build()

  given Conversion[AttributeValue, AttributeValueUpdate] with
    def apply(value: AttributeValue): AttributeValueUpdate =
      AttributeValueUpdate
        .builder()
        .value(value)
        .action(AttributeAction.PUT)
        .build()

  def mapOf(value: (String, AttributeValue)*): AttributeValue =
    AttributeValue
      .builder()
      .m(value.toMap.asJava)
      .build()

  def listOf(value: AttributeValue*): AttributeValue =
    AttributeValue
      .builder()
      .l(value.toSeq.asJava)
      .build()

  private def showMetadata(metadata: DynamoDbResponseMetadata): String =
    s"requestId = ${metadata.requestId()}".stripMargin

  private def showMapAttributeValue(
      value: scala.collection.Map[String, AttributeValue],
      indent: Int = 1
  ): String =
    value
      .map((n, u) => s"${KEY}$n${INFO} = ${showAttributeValue(u)}")
      .mkString("\t" * indent, "\n" + ("\t" * indent), "")

  private def showMapAttributeValueUpdate(
      update: scala.collection.Map[String, AttributeValueUpdate],
      indent: Int = 1
  ): String =
    update
      .map((n, u) => s"${KEY}$n${INFO} = ${showAttributeValueUpdate(u)}")
      .mkString("\t" * indent, "\n" + ("\t" * indent), "")

  private def showAttributeValueUpdate(
      update: AttributeValueUpdate,
      indent: Int = 1
  ): String =
    s"${update.actionAsString()} ${showAttributeValue(update.value())}"

  private def showAttributeValue(
      value: AttributeValue,
      indent: Int = 1
  ): String =
    if (value.s() != null) then
      s"${TYPE}STRING${INFO} \"${VALUE}${value.s()}${INFO}\""
    else if (value.n() != null) then
      s"${TYPE}NUMBER${INFO} ${VALUE}${value.n()}${INFO}"
    else if (value.bool() != null) then
      s"${TYPE}BOOLEAN${INFO} ${VALUE}${value.bool()}${INFO}"
    else if (value.b() != null) then s"${TYPE}BYTES${INFO}"
    else if (value.hasL()) then
      s"${TYPE}LIST${INFO} (${value.l().asScala.map(showAttributeValue(_)).mkString(", ")})${INFO}"
    else if (value.hasBs()) then s"LIST OF BYTES"
    else if (value.hasNs()) then
      s"${TYPE}LIST${INFO} ${VALUE}(${value.ns().asScala.mkString(", ")})${INFO}"
    else if (value.hasSs()) then
      s"${TYPE}LIST${INFO} ${VALUE}(${value.ss().asScala.mkString("\"", "\", \"", "\"")})${INFO}"
    else if (value.hasM()) then
      s"${TYPE}MAP${INFO} \n${VALUE}${showMapAttributeValue(value.m().asScala, indent + 1)}${INFO}"
    else s"${TYPE}UNKNOWN${INFO}"
}
