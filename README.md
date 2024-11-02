# CS441_Fall2024 Assignment 2
Class repository for CS441 on Cloud Computing taught at the University of Illinois, Chicago in Fall, 2024


# Student information
First Name: Manh \
Last Name: Nguyen \
UIN: 650327734 \
UIC Mail: mnguy104@uic.edu \
Link to youtube video: 

# Homework 2
## Description
Description is in [here](./Homeworks/Homework2.md).
## Requirements
The project has been ran by using the following version. \
Scala 2.12.18 \
Hadoop 3.4.0/3.3.6 \
Spark 3.5.3 \
AWS EMR 7.3 \
I haven't got time to test with other version but suspect little/no modification will be required.

## Resources
The project is managed through a typesafe configuration library by this file [application.conf](./src/main/resources/application.conf) . We can specify parameter of each task MapReduce, information about model config, etc,... through this file \
The logger used in the project is Logback. It could be config through [logback.xml](./src/main/resources/logback.xml). Spark also used Log4j2 as a logger so it can also be config through [log4j2.properties](./src/main/resources/log4j2.properties)

## Structure

The scala folder contain main scala code for the project. \
Main function is provided in `main.scala` \
The dataset is managed through `TextDataset.scala` \
Folder `utils` contain Config, Log and Spark implementation \
Folder `model` contain the embedding (from HW1) and LSTM model implementation
- `data` folder contains processing data for training
- `main.scala` contains training code

## Dataset

The dataset used in this assignment is `WikiText`. It contains many Wikipedia articles. \
I downloaded it from [here](https://developer.ibm.com/exchanges/data/all/wikitext-103/) 

File `wiki.train.tokens` contains raw text by combining text from various articles into one large file.
For simplicity, I also used a small fraction of dataset (around 30MB) to train a viable model to reduce computation cost

### DataProcessing
Data is load from input and map to sliding window: [TextDataset.scala](./src/main/scala/TextDataset.scala) \

## Training
I trained using Deeplearning4J with Spark. 

## Training information
The task is computing close words using embedding from Embedding Model. \
I did try `Word2Vec` and it run successfully but it ran out of memory or ran into segmentation fault access so I switch the model to simple two layers Neural network defined by ND4J as professor suggested. \
The idea is using current word token to predict the next word token. \
Each token is produced by `Jtokkit` the same way as the first task, except for simplicity I only take the first element for every word. The wikipedia articles contain many new words and jargons so very likely the Jtokkit will produce a token with more than one integer. For example:  `Hello -> [1234, 21324]` only take `1234` \
The network then produce the probability of the next word based on the embedding and output via activation softmax layer. 

### Dimension of the embedding

The optimal dimension in my experiment is between 6 and 10. Higher dimension could give better accuracy but also increase the computing cost.

# Deployment

## Run locally

Step to run locally:

Set the dataset config in the [application.conf](./src/main/resources/application.conf), as well as the input and output of the mapreduce. Normally, the input of two MapReduce is the output of the dataset.
And then 
```
sbt clean compile run
sbt clean compile test
```

Step to build jar file: The jar file is built using assembly plugin of sbt. The output of the jar file is located at `target/scala-version/`
```
sbt clean compile assembly
```

Command to run locally: 
```
hadoop jar JAR_FILE_NAME.jar
```

## Deploy to EMR

- Create a s3 bucket and upload the JAR file built from previous section into s3 bucket. Copy the path
- Modify the `input/output` path in the `resources/application.conf` to reflect the changes:
For example: 
```
{
    name = "JTokkitMapReduce"
    numberOfMappers = 1
    numberOfReducers = 1
    inputPath = "s3://manhntm3/UIC/CS441Cloud/Dataset/WikiText"
    outputPath = "s3://manhntm3/UIC/CS441Cloud/Dataset/TokenOutput"
}
```
- From the EMR main console, click Create Cluster to start. 
- Start config a cluster, select `EMR-Release 7.3.0`. In the software pre-installed, only select `Hadoop 3.3.6` (discard other choice) 
- Create a cluster configuration: pick One primary One Core One Task in the set up. 
- Other settings: Scaling and provisioning, networking, security. 
- In the steps settings: Select the JAR file built from previous section 


## Limitation
- It take very long to train a useful model. I can test up to 12-16 dimensions but the computation is very expensive when testing with higher dimensions
- I haven't managed to build a proper JAR file and deploy it to EMR yet. I stuck at including deeplearning4j library into a fat jar and run it locally with hadoop
- Lack of combiner in the EmbeddingModel MapReduce implementation, which could increase the performance of the program 
- When deploy into EMR, in the future we could use s3 Java API to directly parse the config file to the program instead of modifying it everytime we change the parameters. The JAR build file contains deeplearning4j library which is very large (>1.5GB) in size, and it take 3-5 minutes just to build the fat jar.
