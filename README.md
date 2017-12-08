# sample-kinesis-android-client
Sample android application as Amazon Kinesis client


## Functions
* Push button and send event to the delivery stream in Amazon Kinesis Firehose.
* Start and stop sensor stream sending sensor data to the stream in Amazon Kinesis Streams. While sensor stream is enabled, the Android device continuously send its tilt calculated from magnetic field and accelerometer on the device.


## Setup

### On AWS services
See http://docs.aws.amazon.com/mobile/sdkforandroid/developerguide/kinesis.html
1. Create a stream in Kinesis Streams
2. Create a delivery stream in Kinesis Firehose
3. Create a Cognito Identity Pool
4. Set IAM permissions to access Kinesis Streams and Kinesis Firehose streams

### In your local
1. Clone this repository
2. Import this application to IDE like Android Studio
3. Copy *copyit.application_settings.xml* to *app/src/main/res/values/*
```bash
$ cp copyit.application_settings.xml app/src/main/res/values/application_settings.xml
```
4. Edit copied file to configure your Cognito Identity Pool ID, region and stream name
4. Run the application on your emulator
5. Build and install to your Android device
6. Yeah! Run the application and find your stream data on the cloud!
