enum ServerError: Error {
    case ApplicationSnapshotFailure
    case SnapshotSerializeFailure
    case AppStateSerializeFailure
}
