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

TCP connection actor doesn't have internal buffering

### [ACK-Based Write Back-Pressure](src/main/scala/com/pjanof/io/WriteBackPressure.scala)

Back-Pressure management where
* Write command carries an arbitrary object
* Object returned to sender upon successfully writing all data to the socket

### [Read Back-Presssure with Pull Mode](src/main/scala/com/pjanof/io/ReadBackPressure.scala)

Back-Pressure management where
* reading is not resumed until previous write has been acknowledged by connection actor
* connection actor starts from suspended state
