**Start Zookeeper**

./bin/zkServer.sh start

**Start user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9090

**Start another user service**

java -cp target/auto_discovery_user-1.0.0-SNAPSHOT-fat.jar application.UserApplication 9091

**Start blog service**

java -cp target/auto_discovery_blog-1.0.0-SNAPSHOT-fat.jar application.BlogApplication 9010