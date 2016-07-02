##Release Notes and News

**Note:** For upgrade instructions please refer to [this page](/resources/upgrade/).

###v0.9.6

November, 2015 | [Release Notes](https://github.com/PredictionIO/PredictionIO/blob/master/RELEASE.md) have been moved to Github and you are reading them. For a detailed list of commits check [this page](https://github.com/PredictionIO/PredictionIO/commits/master)

- Upgrade components for install/runtime to Hbase 1, Spark 1.5.2 PIO still runs on older HBase and Spark back to 1.3.1, upgrading install of Elaticsearch to 1.5.2 since pio run well on it but also runs on older versions.
- Support for maintaining a moving window of events by discarding old events from the EventStore
- Support for doing a deploy without creating a Spark Context

###v0.9.5 

October 14th, 2015 | [Release Notes](https://github.com/PredictionIO/PredictionIO/blob/master/RELEASE.md) have been moved to Github and you are reading them. For a detailed list of commits check [this page](https://github.com/PredictionIO/PredictionIO/commits/v0.9.5)

- Support batches of events sent to the EventServer as json arrays
- Support creating an Elasticsearch StorageClient created for an Elasticsearch cluster from variables in pio-env.sh
- Fixed some errors installing PredictionIO through install.sh when SBT was not correctly downloaded

###v0.9.4

July 16th, 2015 | [Release Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13700)

- Support event permissions with different access keys at the Event Server interface
- Support detection of 3rd party Apache Spark distributions
- Support running `pio eval` without `engine.json`
- Fix an issue where `--verbose` is not handled properly by `pio train`

###v0.9.3

May 20th, 2015 | [Release Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13600)

- Add support of developing prediction engines in Java
- Add support of PostgreSQL and MySQL
- Spark 1.3.1 compatibility fix
- Creation of specific app access keys
- Prevent a case where `pio build` accidentally removes PredictionIO core library

###v0.9.2

April 14th, 2015 | [Release Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13500)

- Channels in the Event Server
- Spark 1.3+ support (upgrade to Spark 1.3+ required)
- [Webhook Connector](http://docs.prediction.io/community/contribute-webhook/) support
- Engine and Event Servers now by default bind to 0.0.0.0
- Many documentation improvements

###v0.9.1

March 17th, 2015 | [Releaes Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13401)

- Improved `pio-start-all`
- Fixed a bug where `pio build` failed to set PredictionIO dependency version for engine templates

###v0.9.0

March 4th, 2015 | [Release Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13400)

- [E-Commerce Recommendation Template](http://templates.prediction.io/PredictionIO/template-scala-parallel-ecommercerecommendation) which includes 1) out-of-stock items support 2) new user recommendation 3) unseen items only
- [Complementary Purchase Template](http://templates.prediction.io/PredictionIO/template-scala-parallel-complementarypurchase) for shopping cart recommendation
- [Lead Scoring Template](http://templates.prediction.io/PredictionIO/template-scala-parallel-leadscoring) predicts the probability of an user will convert in the current session
- `pio-start-all`, `pio-stop-all` commands to start and stop all PredictionIO related services

###v0.8.6

Feb 10th, 2015 | [Release Notes](https://predictionio.atlassian.net/jira/secure/ReleaseNote.jspa?projectId=10000&version=13300)

- New engine template - [Product Ranking](/templates/productranking/quickstart/) for personalized product listing
- [CloudFormation deployment](/system/deploy-cloudformation/) available
