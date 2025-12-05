import java.io.InputStream;
import java.net.URL;

public class ImageFetcher {

    public InputStream fetchImage(String imageUrl) throws Exception {
        // User controls full URL → SSRF
        URL url = new URL(imageUrl);
        return url.openStream();
    }
}