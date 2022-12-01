## Build the UI test
## TODO: make destination generic for iOS 15 simulator
xcodebuild -project ./maestro-driver-ios/maestro-driver-ios.xcodeproj \
  -scheme maestro-driver-ios -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=New,OS=15.5" \
  -xcconfig ./maestro-driver-ios/test-runner-build.xcconfig \
  build-for-testing

# Remove intermediates, output and copy runner in client
rm -r "$TMPDIR"intermediates
mv "$TMPDIR"output/Products/Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app ./maestro-client/src/main/resources
rm -r "$TMPDIR"output

(cd ./maestro-client/src/main/resources/ && zip -r maestro-driver-iosUITests-Runner.zip ./*.app)
rm -r ./maestro-client/src/main/resources/*.app