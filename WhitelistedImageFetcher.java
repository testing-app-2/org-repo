import java.net.URL;

public class WhitelistedImageFetcher {

    public byte[] fetch(String imageUrl) throws Exception {
        if (!imageUrl.startsWith("https://cdn.company.com/") &&
            !imageUrl.startsWith("https://assets.company.com/")) {
            throw new IllegalArgumentException("Only company CDN allowed");
        }
        return new URL(imageUrl).openStream().readAllBytes();
    }
}