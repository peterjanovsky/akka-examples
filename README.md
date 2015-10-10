# akka-examples

Akka Networking Examples

## [Basic](src/main/scala/com/pjanof/io/BasicNetwork.scala)

Client / Server where
* Connection is not aware of previously created Connections
* ephemeral port is assigned to Server and Client

## [PubSub Server and Client](src/main/scala/com/pjanof/io/PubSub.scala)

Client / Server where
* Connection is knowledgeable of previously created Connections
* ephemeral port is assigned to Server and Client

## Back-Pressure

### [Read Back-Presssure with Pull Mode](src/main/scala/com/pjanof/io/ReadBackPressure.scala)

Back-Pressure management where
* reading is not resumed until previous write has been acknowledged by connection actor
* connection actor starts from suspended state
