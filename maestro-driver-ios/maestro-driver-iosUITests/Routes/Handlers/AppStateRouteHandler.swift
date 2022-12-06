import FlyingFox

class AppStateRouteHandler: RouteHandler {
    func handle(request: HTTPRequest) async throws -> HTTPResponse {
        return HTTPResponse(statusCode: .ok)
    }
}
