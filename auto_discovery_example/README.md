**Running using ZooKeeper**

**Start Zookeeper**

./bin/zkServer.sh start

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9090 zookeeper

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9091 zookeeper

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.Main 9010 zookeeper





**Running using Hazelcast**

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9090 hazelcast

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9091 hazelcast

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.Main 9010 hazelcast





**Running using Vertx Discovery**

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9090 vertx true

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.Main 9091 vertx true

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.Main 9010 vertx true