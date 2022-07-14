rootProject.name = "conductor"

include("conductor-android")
include("conductor-android-models")
include("conductor-cli")
include("conductor-client")
include("conductor-ios")
include("conductor-orchestra")
include("conductor-orchestra-models")
include("conductor-test")
include("examples:apps:android-app")
include("examples:samples")
findProject(":examples:samples")?.name = "samples"
