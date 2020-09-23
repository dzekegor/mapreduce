mvn package
scp -r src/main/java/Ranking.java  lima@10.90.138.32:~/Project
scp -r src/main/java/Indexer.java  lima@10.90.138.32:~/Project
scp -r src/main/java/Search.java  lima@10.90.138.32:~/Project
scp -r target/search_engine-1.0-SNAPSHOT.jar  lima@10.90.138.32:~/Project