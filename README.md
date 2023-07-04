# scala-aws-scripts
Handy examples of the Scala scripts performing tasks on AWS using Java SDKv2

<https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html>

## Prerequisites
- Java (JDK) 11 or newer
- Scala-CLI <https://scala-cli.virtuslab.org/>
- Docker for testing

## DynamoDB

- [**dynamoDbScript.sc** - creates a table and updates items](dynamoDbScript.sc)

## Testing with Localstack

```sh
scala-cli test dynamoDbScript.test.scala
```

