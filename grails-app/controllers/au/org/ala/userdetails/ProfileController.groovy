package au.org.ala.userdetails
import org.scribe.builder.ServiceBuilder
import org.scribe.builder.api.FlickrApi
import org.scribe.model.*
import org.scribe.oauth.OAuthService
import org.springframework.web.context.request.RequestContextHolder

class ProfileController {

    def authService
    def oauthService
    def emailService
    def userService

    def index() {

        def user = userService.currentUser

        if (user) {
            def props = user.propsAsMap()
            def isAdmin = RequestContextHolder.currentRequestAttributes()?.isUserInRole("ROLE_ADMIN")
            render(view: "myprofile", model: [user: user, props: props, isAdmin: isAdmin])
        } else {
            String baseUrl = grailsApplication.config.security.cas.loginUrl
            def separator = baseUrl.contains("?") ? "&" : "?"
            def loginUrl = "${baseUrl}${separator}service=" + URLEncoder.encode(emailService.getMyProfileUrl(), "UTF-8")
            redirect(url: loginUrl)
        }
    }

    def flickrCallback() {

        Token token = session.getAt("flickr:oasRequestToken")
        OAuthService service = new ServiceBuilder().
                provider(FlickrApi.class).
                apiKey(grailsApplication.config.oauth.providers.flickr.key).
                apiSecret(grailsApplication.config.oauth.providers.flickr.secret).build()

        Verifier verifer = new Verifier(params.oauth_verifier)
        def accessToken = service.getAccessToken(token, verifer)

        // Now let's go and ask for a protected resource!
        OAuthRequest request = new OAuthRequest(Verb.GET, "http://www.flickr.com/services/oauth/access_token");
        service.signRequest(accessToken, request);
        Response response = request.send();
        def model = [:]
        response.getBody().split("&").each {
            def property = it.substring(0, it.indexOf("="))
            def value = it.substring(it.indexOf("=") + 1)
            model.put(property, value)
        }

        //store the user's flickr ID.
        User user = userService.currentUser

        if (user) {
            //store flickrID & flickrUsername
            UserProperty.addOrUpdateProperty(user, 'flickrId', URLDecoder.decode(model.get("user_nsid"), "UTF-8"))
            UserProperty.addOrUpdateProperty(user, 'flickrUsername', model.get("username"))
        } else {
            flash.message = "Failed to retrieve user details!"
        }

        redirect(controller: 'profile')
    }

    def flickrSuccess() {
        //println "Flickr success called"
        String sessionKey = oauthService.findSessionKeyForAccessToken('twitter')
        //println "Session key: " + request.getSession().getAttribute(sessionKey)
    }

    def flickrFail() {}

    def removeFlickrLink() {
        User user = userService.currentUser
        if (user) {
            UserProperty.findByUserAndProperty(user, 'flickrUsername').delete(flush: true)
            UserProperty.findByUserAndProperty(user, 'flickrId').delete(flush: true)
        } else {
            flash.message = "Failed to retrieve user details!"
        }
        redirect(controller: 'profile')
    }
}
