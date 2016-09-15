*When Running using ZooKeeper*

**Start Zookeeper**

./bin/zkServer.sh start

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9090 zookeeper

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9091 zookeeper

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.BlogApplication 9010 zookeeper



*When Running using Hazelcast*

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9090 hazelcast

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9091 hazelcast

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.BlogApplication 9010 hazelcast



*When Running using Vertx Discovery*

**Start user service**

vertx run src/main/java/application/UserApplication.java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar -conf src/main/resources/conf1.conf

**Start another user service**

vertx run src/main/java/application/UserApplication.java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar -conf src/main/resources/conf2.conf

**Start blog service**

vertx run src/main/java/application/BlogApplication.java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar -conf src/main/resources/conf1.conf