Plaλ!
=====

[ ![Download](https://api.bintray.com/packages/sihil/plambda/plambda/images/download.svg) ](https://bintray.com/sihil/plambda/plambda/_latestVersion)

This is a somewhat comedic attempt to make [Play!](https://www.playframework.com/) apps work in AWS Lambda. It actually succeeds surprisingly well (so long as your expectations are appropriately low).

Getting started
---------------

1. Look at yourself in the mirror and consider whether running your app in AWS Lambda is something you'll be able to live with. Plaλ comes with no warranty and I will provide no counselling for this life choice.
2. Add the Plaλ library to your Play 2.5 project and also include Play's test framework (2.5.x) and the AWS S3 client (1.11.x) - the wrapper relies on these being present, but does not provide them. Add this to your build.sbt:
```
resolvers += "Plambda Releases" at "https://dl.bintray.com/sihil/plambda"
libraryDependencies ++= Seq(
  "net.sihil" %% "plambda" % "0.0.1",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.48",
  component("play-test")
)
```
3. Configure your application to dist a zip file containing all the libraries etc. Add this to the settings for your play project in your SBT configuration:
```
topLevelDirectory in Universal := None
```
4. Run `dist` - this should now create you a zip file suitable for uploading to AWS Lambda
5. Create a CloudFormation stack. I suggest you start with the example template provided and customise as appropriate to grant your application any permissions it needs (note that prior to bringing this up you'll need to upload an initial copy of your lambda into the S3 bucket specified).
   
Known limitations? Plenty
-------------------------

 - Multiple cookies are dealt with by magic so long as they are set on a redirect response (3XX)
 - Multiple identical query parameters are not supported
 - Performance is somewhat poor when the application has not been used recently due to the start up time of Lambda functions. This is somewhat mitigated by using 1.5Gb of RAM as larger amounts of memory also seems to mean a faster CPU. In adition a five minute "ping" is supported by Plambda and used in the CloudFormation which helps to keep the function luke warm at minimal cost.
   
Future work
-----------

 - Try using it with more projects
 - Build an asset interceptor that attempts to re-route known static assets to a static location such as an S3 bucket (seems that this is best done by creating some custom 