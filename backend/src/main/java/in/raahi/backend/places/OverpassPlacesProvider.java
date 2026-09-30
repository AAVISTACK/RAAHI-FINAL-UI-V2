package in.raahi.backend.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class OverpassPlacesProvider implements PlacesProvider {

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    // Maps Raahi's place-type vocabulary (matches the Flutter reference exactly: dhaba,
    // parking, toilet, atm, fuel, hotel) to the OSM tag key/value that identifies it.
    private static final Map<String, String[]> TAG_BY_TYPE = Map.of(
            "dhaba", new String[]{"amenity", "restaurant"},
            "parking", new String[]{"amenity", "parking"},
            "toilet", new String[]{"amenity", "toilets"},
            "atm", new String[]{"amenity", "atm"},
            "fuel", new String[]{"amenity", "fuel"},
            "hotel", new String[]{"tourism", "hotel"}
    );

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<Place> nearby(String placeType, double lat, double lng, double radiusKm) throws PlacesUnavailableException {
        String[] tag = TAG_BY_TYPE.getOrDefault(placeType, TAG_BY_TYPE.get("dhaba"));
        int radiusMeters = (int) (radiusKm * 1000);

        String overpassQuery = String.format(Locale.ROOT,
                "[out:json][timeout:15];(node[\"%s\"=\"%s\"](around:%d,%f,%f)[name];);out body 20;",
                tag[0], tag[1], radiusMeters, lat, lng
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);

        try {
            JsonNode response = restTemplate.postForObject(OVERPASS_URL, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null) throw new PlacesUnavailableException("No response from places provider", null);

            List<Place> places = new ArrayList<>();
            for (JsonNode element : response.path("elements")) {
                double elLat = element.path("lat").asDouble();
                double elLng = element.path("lon").asDouble();
                String name = element.path("tags").path("name").asText(null);
                if (name == null) continue;
                places.add(new Place(element.path("id").asText(), name, elLat, elLng, haversineKm(lat, lng, elLat, elLng)));
            }
            places.sort(Comparator.comparingDouble(p -> p.distanceKm() == null ? Double.MAX_VALUE : p.distanceKm()));
            return places;
        } catch (PlacesUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new PlacesUnavailableException("Could not reach the places provider right now", e);
        }
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
