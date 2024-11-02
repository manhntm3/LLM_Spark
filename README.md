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
JDK 11 \
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
The dataset is managed through `TextDataset.scala` 
- Folder `utils` contain Config, Log and Spark implementation 
- Folder `model` contain the embedding (from HW1) and LSTM model implementation
- Folder `data` folder contains processing data for training
- `main.scala` contains training code

## Dataset

The dataset used in this assignment is `WikiText`. It contains many Wikipedia articles. \
I downloaded it from [here](https://developer.ibm.com/exchanges/data/all/wikitext-103/) 

File `wiki.train.tokens` contains raw text by combining text from various articles into one large file.
For simplicity, I also used a small fraction of dataset (around 30MB) to train a viable model to reduce computation cost. Given the small size of the dataset, when tokenize and compute sliding window embedding, the generated dataset could be five times larger than the original size(~200MB).

### DataProcessing
Data is load from input and map to sliding window: [TextDataset.scala](./src/main/scala/TextDataset.scala) 

Each token is produced by `Jtokkit` the same way as the first homework, except for simplicity I only take the first element for every word. The wikipedia articles contain many new words and jargons so very likely the Jtokkit will produce a token with more than one integer. For example:  `Hello -> [1234, 21324]` only take `1234` \
The network then produce the probability of the next word based on the embedding and output via activation softmax layer. 

I used 10 dimension for the embedding in my experiment. Higher dimension could give better accuracy but also increase the computing cost.

## Training
I trained using Deeplearning4J with Spark. 

## Training information
Training information is done as see in the homework description. 

The model contains a LSTM Neural network layer defined by ND4J as professor suggested. \
The idea is using current sliding window word embedding token to predict the next word. 

We should call `.persist()` before do `.fit()` to keep the dataset in memory and avoid recomputation.

I ran the training for 40 epochs, and the result could be seen as below:


# Deployment

## Run locally

### Step to run locally

Set the training parameter in the config in the [application.conf](./src/main/resources/application.conf) 

Assume `INPUT_DIRECTORY` is a directory contain the dataset, could be in local or in HDFS(e.g: `hdfs://localhost:9000/user/manh/WikiText/`) 

And `OUTPUT_DIRECTORY` is a directory where we want to save the model. Could be in local or in HDFS. The model will be saved as `outputModel.bin`

And then 
```
sbt clean compile "run INPUT_DIRECTORY OUTPUT_DIRECTORY"
sbt clean compile test
```


### Step to build jar file

The jar file is built using assembly plugin of sbt. The output of the jar file is located at `target/scala-version/`
```
sbt -J-Xmx4G clean compile assembly
```

### Run with Spark

Command to submit the job to spark locally: 
```
spark-submit --class SparkAssigment \
--master "local[*]" \
--executor-memory 4G \
--total-executor-cores 4 \
./jar_filename.jar \
INPUT_DIRECTORY OUTPUT_DIRECTORY
```

## Deploy to EMR

- Create a s3 bucket and upload the JAR file built from previous section into s3 bucket. Copy the path and update it to the Custom JAR File in the Steps section when create Cluster.


- From the EMR main console, click Create Cluster to start. 
- Start config a cluster, select `EMR-Release 7.3.0`. In the software pre-installed, select `Hadoop 3.3.6` and `Spark 3.5.0` (discard other choice) 
- Create a cluster configuration: pick One primary And Two Core in the set up. If the dataset is bigger, you might want to increase the number of Core Machine
- Other settings: Scaling and provisioning, networking, security leave as default settings.
- In the steps settings: Select the JAR file built from previous section (s3 path)

Command to run a CustomJAR on EMR:

```
spark-submit --deploy-mode client \
--class SparkAssigment \
--master yarn \
s3://JAR_LOCATION \
s3://INPUT_DIRECTORY s3://OUTPUT_DIRECTORY
```


## Limitation
- When deploy into EMR, in the future we could use s3 Java API to directly parse the config file to the program instead of modifying it everytime we change the parameters. The JAR build file contains deeplearning4j library which is very large (>1.5GB) in size, and it take 3-5 minutes just to build the fat jar.

