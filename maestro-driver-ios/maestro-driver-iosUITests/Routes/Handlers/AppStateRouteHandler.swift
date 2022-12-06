import FlyingFox
import XCTest

class AppStateRouteHandler: RouteHandler {
    func handle(request: HTTPRequest) async throws -> HTTPResponse {
        guard let appId = request.query["appId"] else {
            return HTTPResponse(statusCode: HTTPStatusCode.badRequest)
        }
        
        let app = XCUIApplication(bundleIdentifier: appId)
        
        let state = app.state.toString()
        let response = ["state": state]
        
        guard let responseData = try? JSONSerialization.data(
            withJSONObject: response,
            options: .prettyPrinted
        ) else {
            print("Serialization of app state failed")
            throw ServerError.AppStateSerializeFailure
        }
        
        return HTTPResponse(statusCode: .ok, body: responseData)
    }
}
